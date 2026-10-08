package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.controller.KakaoLoginController;
import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.exception.KakaoAuthException;
import com.ohgiraffers.springdatajpa.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KakaoLoginFlowTests {
    final KakaoLoginService kakao = mock(KakaoLoginService.class);
    final MemberService members = mock(MemberService.class);
    final MockHttpSession session = new MockHttpSession();
    final KakaoUserResponse user = new KakaoUserResponse(42L,
            new KakaoUserResponse.KakaoAccount(new KakaoUserResponse.Profile("연습 사용자", null, null)));
    final MemberDTO member = new MemberDTO(7L, "연습 사용자", null, 123L, 42L, false,
            LocalDateTime.of(2026, 10, 5, 12, 0), LocalDateTime.of(2026, 10, 5, 12, 0), List.of());

    MockMvc mvc(boolean synced) {
        return MockMvcBuilders.standaloneSetup(new KakaoLoginController("test-key",
                "http://localhost:8080/api/auth/kakao/callback", "http://localhost:5173",
                synced, kakao, members)).build();
    }
    void prepare() {
        session.setAttribute(KakaoLoginController.ATTEMPT,
                new KakaoLoginController.LoginAttempt("test-state", System.currentTimeMillis(), false));
        when(kakao.requestToken("test-code")).thenReturn(new KakaoTokenResponse("private-access-token"));
        when(kakao.requestTokenInfo("private-access-token"))
                .thenReturn(new KakaoTokenInfoResponse(42L, 123L, 3600L));
        when(kakao.requestUser("private-access-token")).thenReturn(user);
        when(members.login(123L, user, List.of(), false)).thenReturn(member);
    }

    @Test void loginRequiresKakaoReauthenticationAndCreatesFreshStateWithoutClientSecret() throws Exception {
        var result = mvc(false).perform(get("/api/auth/kakao/login").session(session))
                .andExpect(status().isFound()).andExpect(header().string("Cache-Control", "no-store"))
                .andReturn();
        String location = result.getResponse().getHeader("Location");
        assertTrue(location.startsWith("https://kauth.kakao.com/oauth/authorize?"));
        assertTrue(location.contains("client_id=test-key"));
        assertTrue(location.contains("prompt=login"), "기존 카카오 세션이 있어도 재인증 화면을 요청해야 한다.");
        assertTrue(location.contains("scope=profile_nickname,profile_image,openid"));
        assertFalse(location.contains("client_secret"));
        var first = (KakaoLoginController.LoginAttempt) session.getAttribute(KakaoLoginController.ATTEMPT);
        mvc(false).perform(get("/api/auth/kakao/login").session(session));
        var second = (KakaoLoginController.LoginAttempt) session.getAttribute(KakaoLoginController.ATTEMPT);
        assertNotEquals(first.state(), second.state());
    }

    @Test void callbackCreatesLoginRotatesSessionAndNeverExposesTokens() throws Exception {
        prepare();
        session.setAttribute("existing-payment", "preserved");
        String before = session.getId();
        var mvc = mvc(false);
        var response = mvc.perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state")
                .param("redirect_url", "https://untrusted.example"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=SUCCESS"))
                .andExpect(header().string("Referrer-Policy", "no-referrer")).andReturn().getResponse();
        assertNotEquals(before, session.getId());
        assertEquals("preserved", session.getAttribute("existing-payment"));
        assertEquals(7L, session.getAttribute(KakaoLoginController.MEMBER));
        assertNull(session.getAttribute(KakaoLoginController.ATTEMPT));
        assertFalse(response.getHeader("Location").contains("test-code"));
        assertFalse(response.getHeader("Location").contains("private-access-token"));
        when(members.find(7L)).thenReturn(Optional.of(member));
        var body = mvc.perform(get("/api/auth/me").session(session))
                .andExpect(jsonPath("$.result.authenticated").value(true))
                .andExpect(jsonPath("$.result.user.nickname").value("연습 사용자"))
                .andExpect(header().string("Cache-Control", "no-store"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(body.contains("private-access-token"));
        assertFalse(body.contains("test-key"));
    }

    @Test void repeatedCallbackDoesNotRequestAnotherToken() throws Exception {
        prepare();
        var mvc = mvc(false);
        mvc.perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"));
        mvc.perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=INVALID_STATE"));
        verify(kakao, times(1)).requestToken("test-code");
    }

    @Test void invalidStateStopsBeforeAnyProviderRequest() throws Exception {
        prepare();
        mvc(false).perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "wrong"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=INVALID_STATE"));
        verifyNoInteractions(kakao, members);
    }

    @Test void expiredAttemptStopsBeforeTokenExchange() throws Exception {
        session.setAttribute(KakaoLoginController.ATTEMPT,
                new KakaoLoginController.LoginAttempt("test-state", System.currentTimeMillis() - 700_000, false));
        mvc(false).perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=SESSION_EXPIRED"));
        verifyNoInteractions(kakao, members);
    }

    @Test void missingSessionReturnsFailureWithoutCreatingSession() throws Exception {
        var result = mvc(false).perform(get("/api/auth/kakao/callback").param("code", "test-code"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=SESSION_EXPIRED"))
                .andReturn();
        assertNull(result.getRequest().getSession(false));
        verifyNoInteractions(kakao, members);
    }

    @Test void cancellationNeverSavesMember() throws Exception {
        prepare();
        mvc(false).perform(get("/api/auth/kakao/callback").session(session)
                .param("state", "test-state").param("error", "access_denied"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=CANCELED"));
        verifyNoInteractions(kakao, members);
    }

    @Test void tokenIdentityMismatchStopsMemberLogin() throws Exception {
        prepare();
        when(kakao.requestTokenInfo(anyString())).thenReturn(new KakaoTokenInfoResponse(99L, 123L, 3600L));
        mvc(false).perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=FAILED"));
        verifyNoInteractions(members);
        assertNull(session.getAttribute(KakaoLoginController.MEMBER));
    }

    @Test void logoutRequiresCsrfAndInvalidatesSession() throws Exception {
        session.setAttribute(KakaoLoginController.MEMBER, 7L);
        session.setAttribute(KakaoLoginController.CSRF, "csrf-value");
        var mvc = mvc(false);
        mvc.perform(post("/api/auth/logout").session(session))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(post("/api/auth/logout").session(session).header("X-CSRF-Token", "csrf-value")
                .header("Origin", "http://localhost:5173"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.authenticated").value(false));
        assertTrue(session.isInvalid());
    }

    @Test void logoutRejectsUnexpectedOrigin() throws Exception {
        session.setAttribute(KakaoLoginController.CSRF, "csrf-value");
        mvc(false).perform(post("/api/auth/logout").session(session).header("X-CSRF-Token", "csrf-value")
                .header("Origin", "https://untrusted.example"))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_ORIGIN_INVALID"));
        assertFalse(session.isInvalid());
    }

    @Test void anonymousMeIsFalseAndDoesNotCreateSession() throws Exception {
        var result = mvc(false).perform(get("/api/auth/me"))
                .andExpect(jsonPath("$.result.authenticated").value(false))
                .andExpect(jsonPath("$.result.user").isEmpty()).andReturn();
        assertNull(result.getRequest().getSession(false));
    }

    @Test void syncConsentFailureNeverSavesOrLogsInMember() throws Exception {
        prepare();
        session.setAttribute(KakaoLoginController.ATTEMPT,
                new KakaoLoginController.LoginAttempt("test-state", System.currentTimeMillis(), true));
        when(kakao.requestTerms("private-access-token", 42L)).thenThrow(
                new KakaoAuthException("SYNC_TERMS_REQUIRED", 403, "필수 약관 동의 필요"));
        mvc(true).perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=TERMS_REQUIRED"));
        verifyNoInteractions(members);
        assertNull(session.getAttribute(KakaoLoginController.MEMBER));
    }

    @Test void syncStoresVerifiedConsent() throws Exception {
        prepare();
        var consent = List.of(new KakaoTermsResponse.ServiceTerm("menu_terms_20261005", true, true,
                "2026-10-05T00:00:00Z"));
        session.setAttribute(KakaoLoginController.ATTEMPT,
                new KakaoLoginController.LoginAttempt("test-state", System.currentTimeMillis(), true));
        when(kakao.requestTerms("private-access-token", 42L)).thenReturn(consent);
        when(members.login(123L, user, consent, true)).thenReturn(member);
        mvc(true).perform(get("/api/auth/kakao/callback").session(session)
                .param("code", "test-code").param("state", "test-state"))
                .andExpect(header().string("Location", "http://localhost:5173/auth/result?status=SUCCESS"));
        verify(members).login(123L, user, consent, true);
    }
}
