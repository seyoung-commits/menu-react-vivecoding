package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.KakaoPayException;
import com.ohgiraffers.springdatajpa.exception.MenuNotFoundException;
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
import org.springframework.web.client.RestClient;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class KakaoPayReadyTests {
    private final MenuRepository menus = mock(MenuRepository.class);
    private final MemberRepository members = mock(MemberRepository.class);
    private final KakaoPayGateway gateway = mock(KakaoPayGateway.class);
    private final MockHttpSession session = new MockHttpSession();
    private final KakaoPayService service = new KakaoPayService(menus, members, gateway, mock(com.ohgiraffers.springdatajpa.service.CartService.class), "http://localhost:8080");

    @BeforeEach void login() {
        session.setAttribute(MemberSession.MEMBER, 7L);
        when(members.existsById(7L)).thenReturn(true);
    }

    private void availableMenu(String status, int price) {
        when(menus.findById(1)).thenReturn(Optional.of(new Menu(1, "연습 메뉴", price, status, null)));
        when(gateway.ready(anyMap())).thenReturn(new KakaoPayGateway.ReadyResponse(
                "test-tid", "https://mockup-pg-web.kakao.com/info", "https://mockup-pg-web.kakao.com/mInfo"));
    }

    @Test void usesDatabasePriceAndKeepsTidOnServer() {
        availableMenu("Y", 4500);
        var result = service.ready(new KakaoPayReadyRequest(1, 2), session);
        assertEquals(9000, result.totalAmount());
        var pending = (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        assertEquals("test-tid", pending.tid());
        assertEquals(result.orderId(), pending.orderId());
        assertEquals(9000, pending.totalAmount());
        verify(gateway).ready(argThat(body ->
                body.get("total_amount").equals(9000)
                && body.get("quantity").equals(2)
                && body.get("cid").equals("TC0ONETIME")
                && body.get("approval_url").equals("http://localhost:8080/api/payments/kakaopay/success?orderId=" + result.orderId())));
    }

    @Test void rejectsMissingOrOutOfRangeValuesBeforeCallingProvider() {
        for (Integer quantity : new Integer[]{null, 0, -1, 100}) {
            assertThrows(IllegalArgumentException.class,
                    () -> service.ready(new KakaoPayReadyRequest(1, quantity), session));
        }
        assertThrows(IllegalArgumentException.class,
                () -> service.ready(new KakaoPayReadyRequest(null, 1), session));
        verifyNoInteractions(gateway);
    }

    @Test void rejectsMissingMenu() {
        when(menus.findById(1)).thenReturn(Optional.empty());
        assertThrows(MenuNotFoundException.class, () -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        verifyNoInteractions(gateway);
    }

    @Test void rejectsUnavailableMenuAndUnsafeAmounts() {
        availableMenu("N", 4500);
        assertThrows(IllegalArgumentException.class, () -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        availableMenu("Y", Integer.MAX_VALUE);
        assertThrows(IllegalArgumentException.class, () -> service.ready(new KakaoPayReadyRequest(1, 2), session));
        availableMenu("Y", 0);
        assertThrows(IllegalArgumentException.class, () -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        verify(gateway, never()).ready(anyMap());
    }

    @Test void keepsSeparateOrdersButUsesAuthenticatedMemberNumber() {
        availableMenu("Y", 4500);
        service.ready(new KakaoPayReadyRequest(1, 1), session);
        var first = (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        service.ready(new KakaoPayReadyRequest(1, 1), session);
        var second = (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        assertNotEquals(first.orderId(), second.orderId());
        assertEquals(first.userId(), second.userId());
        assertEquals("member-7", second.userId());
    }

    @Test void failedReadyDoesNotOverwritePendingPayment() {
        availableMenu("Y", 4500);
        service.ready(new KakaoPayReadyRequest(1, 1), session);
        Object previous = session.getAttribute(KakaoPayService.PENDING_PAYMENT);
        when(gateway.ready(anyMap())).thenThrow(new KakaoPayException("KAKAOPAY_UNAVAILABLE", 502, "통신 실패"));
        assertThrows(KakaoPayException.class, () -> service.ready(new KakaoPayReadyRequest(1, 1), session));
        assertSame(previous, session.getAttribute(KakaoPayService.PENDING_PAYMENT));
    }

    @Test void sendsJsonAndSecretHeaderAndReadsProviderSnakeCase() {
        var builder = RestClient.builder().baseUrl("https://open-api.kakaopay.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var actual = new KakaoPayGateway(builder.build(), "TC0ONETIME", "fake-test-key");
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/ready"))
                .andExpect(method(org.springframework.http.HttpMethod.POST))
                .andExpect(header("Authorization", "SECRET_KEY fake-test-key"))
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(request -> {
                    var json = new JsonMapper().readTree(((MockClientHttpRequest) request).getBodyAsString());
                    assertEquals(9000, json.get("total_amount").asInt());
                    assertEquals("TC0ONETIME", json.get("cid").asText());
                })
                .andRespond(withSuccess("""
                    {"tid":"test-tid","next_redirect_pc_url":"https://mockup-pg-web.kakao.com/info",
                     "next_redirect_mobile_url":"https://mockup-pg-web.kakao.com/mInfo","created_at":"2026-09-30"}
                    """, MediaType.APPLICATION_JSON));
        assertEquals("test-tid", actual.ready(Map.of("cid", "TC0ONETIME", "total_amount", 9000)).tid());
        server.verify();
    }

    @Test void refusesMissingKeyAndProductionCid() {
        var missing = new KakaoPayGateway(RestClient.create(), "TC0ONETIME", "");
        assertEquals("KAKAOPAY_KEY_MISSING", assertThrows(KakaoPayException.class, missing::checkConfigured).getCode());
        var production = new KakaoPayGateway(RestClient.create(), "LIVE_CID", "fake-test-key");
        assertEquals("KAKAOPAY_TEST_ONLY", assertThrows(KakaoPayException.class, production::checkConfigured).getCode());
    }

    @Test void doesNotExposeProviderErrorBody() {
        var builder = RestClient.builder().baseUrl("https://open-api.kakaopay.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var actual = new KakaoPayGateway(builder.build(), "TC0ONETIME", "fake-test-key");
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/ready"))
                .andRespond(withStatus(org.springframework.http.HttpStatus.UNAUTHORIZED)
                        .body("sensitive-provider-error fake-test-key"));
        var error = assertThrows(KakaoPayException.class, () -> actual.ready(Map.of()));
        assertEquals("KAKAOPAY_AUTH_FAILED", error.getCode());
        assertFalse(error.getMessage().contains("sensitive-provider-error"));
        assertFalse(error.getMessage().contains("fake-test-key"));
        server.verify();
    }

    @Test void rejectsBrokenProviderResponse() {
        var builder = RestClient.builder().baseUrl("https://open-api.kakaopay.com");
        var server = MockRestServiceServer.bindTo(builder).build();
        var actual = new KakaoPayGateway(builder.build(), "TC0ONETIME", "fake-test-key");
        server.expect(requestTo("https://open-api.kakaopay.com/online/v1/payment/ready"))
                .andRespond(withSuccess("{\"tid\":\"test-tid\",\"next_redirect_pc_url\":\"javascript:alert(1)\"}", MediaType.APPLICATION_JSON));
        assertEquals("KAKAOPAY_READY_FAILED",
                assertThrows(KakaoPayException.class, () -> actual.ready(Map.of())).getCode());
        server.verify();
    }
}

