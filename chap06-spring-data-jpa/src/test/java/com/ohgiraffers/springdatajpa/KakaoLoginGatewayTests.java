package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.service.KakaoLoginService;
import com.ohgiraffers.springdatajpa.exception.KakaoAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.http.*;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoLoginGatewayTests {
    final RestClient.Builder tokenBuilder = RestClient.builder().baseUrl("https://kauth.kakao.com");
    final RestClient.Builder apiBuilder = RestClient.builder().baseUrl("https://kapi.kakao.com");
    final MockRestServiceServer tokenServer = MockRestServiceServer.bindTo(tokenBuilder).build();
    final MockRestServiceServer apiServer = MockRestServiceServer.bindTo(apiBuilder).build();
    final KakaoLoginService service = new KakaoLoginService("fake-key", "fake-secret",
            "http://localhost:8080/api/auth/kakao/callback", "menu_terms_20261005",
            tokenBuilder.build(), apiBuilder.build());

    @Test void tokenRequestUsesFormAndParsesAccessToken() {
        tokenServer.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_FORM_URLENCODED))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("client_secret=fake-secret")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("code=fake-code")))
                .andRespond(withSuccess("{\"access_token\":\"fake-token\",\"refresh_token\":\"ignored\"}", MediaType.APPLICATION_JSON));
        assertEquals("fake-token", service.requestToken("fake-code").accessToken());
        tokenServer.verify();
    }

    @Test void userRequestUsesBearerAndNestedProfile() {
        apiServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-token"))
                .andRespond(withSuccess("""
                    {"id":42,"kakao_account":{"profile":{"nickname":"연습",
                    "profile_image_url":"https://k.kakaocdn.net/dn/test/photo.jpg",
                    "thumbnail_image_url":"https://k.kakaocdn.net/dn/test/thumb.jpg"},"other":true}}
                    """, MediaType.APPLICATION_JSON));
        var profile = service.requestUser("fake-token").kakaoAccount().profile();
        assertEquals("연습", profile.nickname());
        assertEquals("https://k.kakaocdn.net/dn/test/photo.jpg", profile.profileImageUrl());
        assertEquals("https://k.kakaocdn.net/dn/test/thumb.jpg", profile.thumbnailImageUrl());
        apiServer.verify();
    }

    @Test void nicknameCanBeAbsent() {
        apiServer.expect(requestTo("https://kapi.kakao.com/v2/user/me"))
                .andRespond(withSuccess("{\"id\":42}", MediaType.APPLICATION_JSON));
        assertNull(service.requestUser("fake-token").kakaoAccount());
    }

    @Test void tokenInfoIncludesProviderAppId() {
        apiServer.expect(requestTo("https://kapi.kakao.com/v1/user/access_token_info"))
                .andRespond(withSuccess("{\"id\":42,\"app_id\":123,\"expires_in\":3600}", MediaType.APPLICATION_JSON));
        assertEquals(123L, service.requestTokenInfo("fake-token").appId());
    }

    void terms(String body) {
        apiServer.expect(requestTo("https://kapi.kakao.com/v2/user/service_terms?result=app_service_terms"))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer fake-token"))
                .andRespond(withSuccess(body, MediaType.APPLICATION_JSON));
    }
    @Test void readsRequiredAndOptionalConsentWithoutForcingOptionalConsent() {
        terms("""
            {"id":42,"service_terms":[
              {"tag":"menu_terms_20261005","required":true,"agreed":true,"agreed_at":"2026-10-05T00:00:00Z"},
              {"tag":"optional_practice","required":false,"agreed":false}]}
            """);
        assertEquals(2, service.requestTerms("fake-token", 42L).size());
    }
    @Test void rejectsMissingRequiredTerms() {
        terms("{\"id\":42,\"service_terms\":[]}");
        assertEquals("SYNC_TERMS_REQUIRED", assertThrows(KakaoAuthException.class,
                () -> service.requestTerms("fake-token", 42L)).getCode());
    }
    @Test void rejectsUnagreedRequiredTerm() {
        terms("""
            {"id":42,"service_terms":[{"tag":"menu_terms_20261005","required":true,"agreed":false}]}
            """);
        assertEquals("SYNC_TERMS_REQUIRED", assertThrows(KakaoAuthException.class,
                () -> service.requestTerms("fake-token", 42L)).getCode());
    }
    @Test void rejectsConsentForDifferentUser() {
        terms("{\"id\":99,\"service_terms\":[]}");
        assertEquals("AUTH_PROVIDER_RESPONSE", assertThrows(KakaoAuthException.class,
                () -> service.requestTerms("fake-token", 42L)).getCode());
    }
    @Test void providerErrorDoesNotLeakResponseBody() {
        tokenServer.expect(requestTo("https://kauth.kakao.com/oauth/token"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).body("private-provider-response-with-token"));
        var error = assertThrows(KakaoAuthException.class, () -> service.requestToken("fake-code"));
        assertFalse(error.getMessage().contains("private-provider-response"));
        assertNull(error.getCause());
    }
}
