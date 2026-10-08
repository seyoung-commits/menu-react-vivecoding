package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.controller.KakaoPayController;
import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.ExceptionController;
import com.ohgiraffers.springdatajpa.exception.KakaoPayException;
import com.ohgiraffers.springdatajpa.repository.MenuRepository;
import com.ohgiraffers.springdatajpa.repository.MemberRepository;
import com.ohgiraffers.springdatajpa.service.MemberSession;
import org.junit.jupiter.api.BeforeEach;
import com.ohgiraffers.springdatajpa.service.KakaoPayGateway;
import com.ohgiraffers.springdatajpa.service.KakaoPayService;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class KakaoPayApproveTests {
    final MenuRepository menus = mock(MenuRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final KakaoPayGateway gateway = mock(KakaoPayGateway.class);
    final MockHttpSession session = new MockHttpSession();
    final KakaoPayService service = new KakaoPayService(menus, members, gateway, mock(com.ohgiraffers.springdatajpa.service.CartService.class), "http://localhost:8080");

    @BeforeEach void login() {
        session.setAttribute(MemberSession.MEMBER, 7L);
        when(members.existsById(7L)).thenReturn(true);
    }

    KakaoPayService.PendingPayment prepare() {
        when(menus.findById(1)).thenReturn(Optional.of(new Menu(1, "연습 메뉴", 4500, "Y", null)));
        when(gateway.ready(anyMap())).thenReturn(new KakaoPayGateway.ReadyResponse(
                "test-tid", "https://mockup-pg-web.kakao.com/info", "https://mockup-pg-web.kakao.com/mInfo"));
        service.ready(new KakaoPayReadyRequest(1, 2), session);
        return (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
    }

    KakaoPayGateway.PaymentResponse provider(KakaoPayService.PendingPayment pending, String status) {
        return new KakaoPayGateway.PaymentResponse(pending.tid(), "TC0ONETIME", status,
                pending.orderId(), pending.userId(), pending.quantity(),
                new KakaoPayGateway.Amount(pending.totalAmount()), "MONEY", "2026-09-30T18:00:00");
    }

    @Test void approvesUsingStoredOrderAndAmountThenCachesRefreshResult() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenReturn(provider(pending, null));
        var result = service.approve(pending.orderId(), "fake-pg-token", session);
        assertEquals(KakaoPayService.Status.APPROVED, result.status());
        assertEquals(9000, result.totalAmount());
        assertSame(result, service.approve(pending.orderId(), "fake-pg-token", session));
        verify(gateway, times(1)).approve(argThat(body ->
                pending.orderId().equals(body.get("partner_order_id"))
                && pending.userId().equals(body.get("partner_user_id"))
                && "test-tid".equals(body.get("tid"))
                && "fake-pg-token".equals(body.get("pg_token"))
                && Integer.valueOf(9000).equals(body.get("total_amount"))));
        assertFalse(result.toString().contains("fake-pg-token"));
    }

    @Test void rejectsAnotherOrderAnotherSessionAndMissingTokenBeforeCallingApprove() {
        var pending = prepare();
        assertEquals("KAKAOPAY_SESSION_NOT_FOUND", assertThrows(KakaoPayException.class,
                () -> service.approve("another-order", "token", session)).getCode());
        assertThrows(KakaoPayException.class,
                () -> service.approve(pending.orderId(), "token", new MockHttpSession()));
        assertThrows(IllegalArgumentException.class,
                () -> service.approve(pending.orderId(), "", session));
        verify(gateway, never()).approve(anyMap());
    }

    @Test void rejectsExpiredReadyInformation() {
        var pending = prepare();
        session.setAttribute(KakaoPayService.PENDING_PAYMENT, new KakaoPayService.PendingPayment(
                pending.orderId(), pending.userId(), pending.tid(), pending.menuCode(),
                pending.menuName(), pending.quantity(), pending.totalAmount(), Instant.now().minusSeconds(1900)));
        assertEquals("KAKAOPAY_SESSION_EXPIRED", assertThrows(KakaoPayException.class,
                () -> service.approve(pending.orderId(), "token", session)).getCode());
        verify(gateway, never()).approve(anyMap());
    }

    @Test void cancelAndFailurePreventLaterApproval() {
        var pending = prepare();
        assertEquals(KakaoPayService.Status.CANCELED, service.abort(pending.orderId(), false, session).status());
        assertThrows(KakaoPayException.class, () -> service.approve(pending.orderId(), "token", session));
        var failedPending = prepare();
        assertEquals(KakaoPayService.Status.FAILED, service.abort(failedPending.orderId(), true, session).status());
        verify(gateway, never()).approve(anyMap());
    }

    @Test void lateCancelCannotReplaceApprovedResult() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenReturn(provider(pending, null));
        var approved = service.approve(pending.orderId(), "token", session);
        assertSame(approved, service.abort(pending.orderId(), false, session));
        assertSame(approved, service.status(pending.orderId(), session));
    }

    @Test void recoversLostApproveResponseUsingOrderQueryWithoutSecondApproval() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenThrow(new KakaoPayException("KAKAOPAY_UNAVAILABLE", 502, "응답 유실"));
        when(gateway.findOrder(pending.tid())).thenReturn(provider(pending, "SUCCESS_PAYMENT"));
        assertEquals(KakaoPayService.Status.APPROVED, service.approve(pending.orderId(), "token", session).status());
        service.approve(pending.orderId(), "token", session);
        verify(gateway, times(1)).approve(anyMap());
        verify(gateway, times(1)).findOrder(pending.tid());
    }

    @Test void keepsUnknownAndBlocksNewReadyUntilConfirmed() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenThrow(new KakaoPayException("KAKAOPAY_UNAVAILABLE", 502, "통신 실패"));
        when(gateway.findOrder(pending.tid())).thenThrow(new KakaoPayException("KAKAOPAY_UNAVAILABLE", 502, "조회 실패"));
        assertEquals(KakaoPayService.Status.UNKNOWN, service.approve(pending.orderId(), "token", session).status());
        assertEquals(KakaoPayService.Status.UNKNOWN, service.approve(pending.orderId(), "token", session).status());
        assertEquals("KAKAOPAY_APPROVAL_UNCERTAIN", assertThrows(KakaoPayException.class,
                () -> service.ready(new KakaoPayReadyRequest(1, 1), session)).getCode());
        verify(gateway, times(1)).approve(anyMap());
        doReturn(provider(pending, "SUCCESS_PAYMENT")).when(gateway).findOrder(pending.tid());
        assertEquals(KakaoPayService.Status.APPROVED, service.status(pending.orderId(), session).status());
        service.ready(new KakaoPayReadyRequest(1, 1), session);
        assertNull(session.getAttribute(KakaoPayService.PAYMENT_RESULT));
    }

    @Test void doesNotTrustAuthenticationOnlyAsPaymentComplete() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenThrow(new KakaoPayException("KAKAOPAY_APPROVE_FAILED", 502, "승인 실패"));
        when(gateway.findOrder(pending.tid())).thenReturn(provider(pending, "AUTH_PASSWORD"));
        assertEquals(KakaoPayService.Status.UNKNOWN, service.approve(pending.orderId(), "token", session).status());
    }

    @Test void rejectsMismatchedAmountAndUserInProviderResults() {
        var pending = prepare();
        var wrongAmount = new KakaoPayGateway.PaymentResponse(pending.tid(), "TC0ONETIME", null,
                pending.orderId(), pending.userId(), 2, new KakaoPayGateway.Amount(1), "MONEY", "2026-09-30T18:00:00");
        var wrongUser = new KakaoPayGateway.PaymentResponse(pending.tid(), "TC0ONETIME", "SUCCESS_PAYMENT",
                pending.orderId(), "another-user", 2, new KakaoPayGateway.Amount(9000), "MONEY", "2026-09-30T18:00:00");
        when(gateway.approve(anyMap())).thenReturn(wrongAmount);
        when(gateway.findOrder(pending.tid())).thenReturn(wrongUser);
        assertEquals(KakaoPayService.Status.UNKNOWN, service.approve(pending.orderId(), "token", session).status());
    }

    @Test void mapsConfirmedProviderFailureToFailed() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenThrow(new KakaoPayException("KAKAOPAY_APPROVE_FAILED", 502, "승인 거절"));
        when(gateway.findOrder(pending.tid())).thenReturn(provider(pending, "FAIL_PAYMENT"));
        assertEquals(KakaoPayService.Status.FAILED, service.approve(pending.orderId(), "token", session).status());
    }

    @Test void postsApproveAndOrderToOfficialEndpoints() {
        var builder = RestClient.builder().baseUrl("https://open-api.kakaopay.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var actual = new KakaoPayGateway(builder.build(), "TC0ONETIME", "fake-test-key");
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/approve"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "SECRET_KEY fake-test-key"))
                .andExpect(request -> {
                    var body = new JsonMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("fake-token", body.get("pg_token").asText());
                    assertEquals(9000, body.get("total_amount").asInt());
                }).andRespond(withSuccess("""
                    {"cid":"TC0ONETIME","tid":"test-tid","partner_order_id":"order","partner_user_id":"user",
                     "quantity":2,"amount":{"total":9000,"vat":818},"payment_method_type":"MONEY",
                     "approved_at":"2026-09-30T18:00:00","aid":"ignored-aid"}
                    """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/order"))
                .andExpect(request -> {
                    var body = new JsonMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals("test-tid", body.get("tid").asText());
                    assertEquals("TC0ONETIME", body.get("cid").asText());
                }).andRespond(withSuccess("{\"status\":\"SUCCESS_PAYMENT\"}", MediaType.APPLICATION_JSON));
        assertEquals(9000, actual.approve(Map.of("pg_token", "fake-token", "total_amount", 9000)).amount().total());
        assertEquals("SUCCESS_PAYMENT", actual.findOrder("test-tid").status());
        server.verify();
    }

    @Test void callbackReturnsSafeJsonAndMissingSessionReturnsStructuredError() throws Exception {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenReturn(provider(pending, null));
        var mvc = MockMvcBuilders.standaloneSetup(new KakaoPayController(service))
                .setControllerAdvice(new ExceptionController()).build();
        mvc.perform(get("/api/payments/kakaopay/success").session(session)
                    .param("orderId", pending.orderId()).param("pg_token", "fake-token"))
                .andExpect(status().isOk()).andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.result.payment.status").value("APPROVED"))
                .andExpect(jsonPath("$.result.payment.totalAmount").value(9000))
                .andExpect(content().string(org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("fake-token"))));
        mvc.perform(get("/api/payments/kakaopay/success")
                    .param("orderId", pending.orderId()).param("pg_token", "fake-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
    }
}

