package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.controller.KakaoPayController;
import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.ExceptionController;
import com.ohgiraffers.springdatajpa.repository.MenuRepository;
import com.ohgiraffers.springdatajpa.repository.MemberRepository;
import com.ohgiraffers.springdatajpa.service.MemberSession;
import org.junit.jupiter.api.BeforeEach;
import com.ohgiraffers.springdatajpa.service.KakaoPayGateway;
import com.ohgiraffers.springdatajpa.service.KakaoPayService;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KakaoPayAppFlowTests {
    final MenuRepository menus = mock(MenuRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final KakaoPayGateway gateway = mock(KakaoPayGateway.class);
    final MockHttpSession session = new MockHttpSession();
    final KakaoPayService service = new KakaoPayService(menus, members, gateway, mock(com.ohgiraffers.springdatajpa.service.CartService.class), "http://localhost:8080");

    @BeforeEach void login() {
        session.setAttribute(MemberSession.MEMBER, 7L);
        when(members.existsById(7L)).thenReturn(true);
    }

    KakaoPayService.PendingPayment prepareAppPayment() {
        when(menus.findById(1)).thenReturn(Optional.of(new Menu(1, "연습 메뉴", 4500, "Y", null)));
        when(gateway.ready(anyMap())).thenReturn(new KakaoPayGateway.ReadyResponse(
                "test-tid", "https://mockup-pg-web.kakao.com/info", "https://mockup-pg-web.kakao.com/mInfo"));
        var result = service.ready(new KakaoPayReadyRequest(1, 2), session, true);
        verify(gateway).ready(argThat(body ->
                body.get("approval_url").equals("http://localhost:8080/api/payments/kakaopay/success?orderId=" + result.orderId() + "&returnToApp=true")
                && body.get("cancel_url").toString().endsWith("&returnToApp=true")
                && body.get("fail_url").toString().endsWith("&returnToApp=true")));
        return (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
    }

    @Test void redirectsApprovedAppPaymentWithoutLeakingTokenAndStatusUsesSameSession() throws Exception {
        var pending = prepareAppPayment();
        when(gateway.approve(anyMap())).thenReturn(new KakaoPayGateway.PaymentResponse(
                pending.tid(), "TC0ONETIME", null, pending.orderId(), pending.userId(),
                pending.quantity(), new KakaoPayGateway.Amount(9000), "MONEY", "2026-09-30T18:00:00"));
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new ExceptionController()).build();
        var response = mvc.perform(get("/api/payments/kakaopay/success").session(session)
                .param("orderId", pending.orderId()).param("pg_token", "fake-sensitive-token")
                .param("returnToApp", "true").param("redirect_url", "https://untrusted.example"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:5173/payments/result?orderId=" + pending.orderId()))
                .andReturn().getResponse();
        assertFalse(response.getHeader("Location").contains("fake-sensitive-token"));
        mvc.perform(get("/api/payments/kakaopay/status").session(session).param("orderId", pending.orderId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.result.payment.status").value("APPROVED"));
        mvc.perform(get("/api/payments/kakaopay/status").param("orderId", pending.orderId()))
                .andExpect(status().isUnauthorized()).andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }

    @Test void returnsCanceledAppPaymentToResultPage() throws Exception {
        var pending = prepareAppPayment();
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service)).build();
        mvc.perform(get("/api/payments/kakaopay/cancel").session(session)
                .param("orderId", pending.orderId()).param("returnToApp", "true"))
                .andExpect(status().isSeeOther());
        mvc.perform(get("/api/payments/kakaopay/status").session(session).param("orderId", pending.orderId()))
                .andExpect(jsonPath("$.result.payment.status").value("CANCELED"));
        verify(gateway, never()).approve(anyMap());
    }

    @Test void expiredSessionReturnsToAppSoResultPageCanExplainTheError() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new ExceptionController()).build();
        mvc.perform(get("/api/payments/kakaopay/success").param("orderId", "missing-order")
                .param("pg_token", "token").param("returnToApp", "true"))
                .andExpect(status().isSeeOther())
                .andExpect(header().string("Location", "http://localhost:5173/payments/result?orderId=missing-order"));
        verifyNoInteractions(gateway);
    }
}

