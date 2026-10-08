package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.controller.KakaoPayController;
import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.*;
import com.ohgiraffers.springdatajpa.repository.*;
import com.ohgiraffers.springdatajpa.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KakaoPayLoginRequiredTests {
    final MenuRepository menus = mock(MenuRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final KakaoPayGateway gateway = mock(KakaoPayGateway.class);
    final KakaoPayService service = new KakaoPayService(menus, members, gateway, mock(com.ohgiraffers.springdatajpa.service.CartService.class), "http://localhost:8080");

    @Test void anonymousDirectReadyRequestReturns401WithoutContactingKakaoPay() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new ExceptionController()).build();
        mvc.perform(post("/api/payments/kakaopay/ready").contentType(MediaType.APPLICATION_JSON)
                .content("{\"menuCode\":1,\"quantity\":1,\"memberCode\":7}"))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        verifyNoInteractions(gateway, menus, members);
    }

    @Test void anonymousCannotApproveCancelFailOrReadResults() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new ExceptionController()).build();
        for (String action : new String[] {"success", "cancel", "fail", "status"}) {
            mvc.perform(get("/api/payments/kakaopay/" + action)
                    .param("orderId", "known-order").param("pg_token", "token"))
                    .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        }
        verifyNoInteractions(gateway, menus, members);
    }

    @Test void missingDeletedMalformedAndInvalidatedLoginCannotStartPayment() {
        assertLoginRequired(() -> service.ready(new KakaoPayReadyRequest(1, 1), null));
        var session = new MockHttpSession();
        session.setAttribute(MemberSession.MEMBER, "7");
        assertLoginRequired(() -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        session.setAttribute(MemberSession.MEMBER, 7L); // DB에서 삭제된 회원
        assertLoginRequired(() -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        session.invalidate();
        assertLoginRequired(() -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        verifyNoInteractions(gateway, menus);
    }

    MockHttpSession prepare() {
        var session = new MockHttpSession();
        session.setAttribute(MemberSession.MEMBER, 7L);
        when(members.existsById(7L)).thenReturn(true);
        when(menus.findById(1)).thenReturn(Optional.of(new Menu(1, "연습 메뉴", 4500, "Y", null)));
        when(gateway.ready(anyMap())).thenReturn(new KakaoPayGateway.ReadyResponse(
                "test-tid", "https://mockup-pg-web.kakao.com/info", "https://mockup-pg-web.kakao.com/mInfo"));
        service.ready(new KakaoPayReadyRequest(1, 2), session);
        clearInvocations(gateway);
        return session;
    }

    @Test void logoutAfterReadyBlocksApprovalAndResultQueriesBeforeProviderCalls() {
        var session = prepare();
        var pending = (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        session.removeAttribute(MemberSession.MEMBER);
        assertLoginRequired(() -> service.approve(pending.orderId(), "token", session));
        assertLoginRequired(() -> service.status(pending.orderId(), session));
        assertLoginRequired(() -> service.abort(pending.orderId(), false, session));
        verifyNoInteractions(gateway);
    }

    @Test void anotherMemberInSameSessionCannotActOnPreviousMembersOrder() {
        var session = prepare();
        var pending = (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        assertEquals("member-7", pending.userId());
        session.setAttribute(MemberSession.MEMBER, 8L);
        when(members.existsById(8L)).thenReturn(true);
        assertEquals("PAYMENT_FORBIDDEN", assertThrows(KakaoPayException.class,
                () -> service.approve(pending.orderId(), "token", session)).getCode());
        assertEquals("PAYMENT_FORBIDDEN", assertThrows(KakaoPayException.class,
                () -> service.status(pending.orderId(), session)).getCode());
        assertEquals("PAYMENT_FORBIDDEN", assertThrows(KakaoPayException.class,
                () -> service.abort(pending.orderId(), false, session)).getCode());
        verifyNoInteractions(gateway);
    }

    void assertLoginRequired(org.junit.jupiter.api.function.Executable action) {
        var error = assertThrows(KakaoPayException.class, action);
        assertEquals("LOGIN_REQUIRED", error.getCode());
        assertEquals(401, error.getStatus());
    }
}
