package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.dto.PaymentItem;
import com.ohgiraffers.springdatajpa.exception.*;
import com.ohgiraffers.springdatajpa.repository.*;
import com.ohgiraffers.springdatajpa.service.*;
import org.junit.jupiter.api.*;
import org.springframework.mock.web.MockHttpSession;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CartPaymentTests {
    final MenuRepository menus = mock(MenuRepository.class);
    final MemberRepository members = mock(MemberRepository.class);
    final KakaoPayGateway gateway = mock(KakaoPayGateway.class);
    final CartService carts = mock(CartService.class);
    final KakaoPayService payments = new KakaoPayService(menus, members, gateway, carts, "http://localhost:8080");
    final MockHttpSession session = new MockHttpSession();
    final List<PaymentItem> items = List.of(new PaymentItem(10L, 0L, 1, "김치찌개", 4500, 2, 9000),
            new PaymentItem(11L, 2L, 2, "콜라", 1200, 1, 1200));

    @BeforeEach void login() {
        session.setAttribute(MemberSession.MEMBER, 7L);
        session.setAttribute("AUTH_CSRF_TOKEN", "csrf");
        when(members.existsById(7L)).thenReturn(true);
        when(carts.checkout(7L)).thenReturn(new CartService.Checkout(7L, items, "김치찌개 외 1종", 3, 10200));
        when(gateway.ready(anyMap())).thenReturn(new KakaoPayGateway.ReadyResponse(
                "test-tid", "https://mockup-pg-web.kakao.com/info", "https://mockup-pg-web.kakao.com/mInfo"));
    }
    KakaoPayService.PendingPayment prepare() {
        payments.readyCart(session, true, "csrf");
        return (KakaoPayService.PendingPayment) session.getAttribute(KakaoPayService.PENDING_PAYMENT);
    }
    KakaoPayGateway.PaymentResponse approved(KakaoPayService.PendingPayment pending) {
        return new KakaoPayGateway.PaymentResponse(pending.tid(), "TC0ONETIME", "SUCCESS_PAYMENT",
                pending.orderId(), pending.userId(), 3, new KakaoPayGateway.Amount(10200), "MONEY", "2026-10-06T12:00:00");
    }

    @Test void readyAggregatesServerCartAndKeepsSnapshotWithoutClearingIt() {
        var pending = prepare();
        assertTrue(pending.fromCart());
        assertEquals(items, pending.items());
        verify(gateway).ready(argThat(body -> body.get("total_amount").equals(10200)
                && body.get("quantity").equals(3) && body.get("item_name").equals("김치찌개 외 1종")
                && body.get("partner_user_id").equals("member-7")));
        verify(carts, never()).completePurchase(anyLong(), anyList());
    }

    @Test void approvalClearsPurchasedSnapshotOnceAndRepeatedCallbacksNeverReapprove() {
        var pending = prepare();
        when(gateway.approve(anyMap())).thenReturn(approved(pending));
        var result = payments.approve(pending.orderId(), "token", session);
        assertEquals(KakaoPayService.Status.APPROVED, result.status());
        assertEquals(2, result.items().size());
        assertFalse(result.cartCleanupPending());
        payments.approve(pending.orderId(), "token", session);
        payments.status(pending.orderId(), session);
        verify(gateway, times(1)).approve(anyMap());
        verify(carts, times(1)).completePurchase(7L, items);
    }

    @Test void canceledFailedAndUnknownPaymentsKeepCart() {
        var canceled = prepare(); payments.abort(canceled.orderId(), false, session);
        var failed = prepare(); payments.abort(failed.orderId(), true, session);
        var uncertain = prepare();
        when(gateway.approve(anyMap())).thenThrow(new KakaoPayException("UNAVAILABLE", 502, "응답 유실"));
        when(gateway.findOrder(anyString())).thenThrow(new KakaoPayException("UNAVAILABLE", 502, "조회 실패"));
        assertEquals(KakaoPayService.Status.UNKNOWN, payments.approve(uncertain.orderId(), "token", session).status());
        verify(carts, never()).completePurchase(anyLong(), anyList());
    }

    @Test void cleanupFailureDoesNotTurnApprovedPaymentIntoFailureAndStatusRetriesOnlyCleanup() {
        var pending = prepare(); when(gateway.approve(anyMap())).thenReturn(approved(pending));
        doThrow(new IllegalStateException("DB unavailable")).doNothing().when(carts).completePurchase(7L, items);
        var result = payments.approve(pending.orderId(), "token", session);
        assertEquals(KakaoPayService.Status.APPROVED, result.status());
        assertTrue(result.cartCleanupPending());
        assertFalse(payments.status(pending.orderId(), session).cartCleanupPending());
        verify(gateway, times(1)).approve(anyMap());
        verify(carts, times(2)).completePurchase(7L, items);
    }

    @Test void missingLoginBadCsrfAndUnavailableCartNeverCallProvider() {
        assertEquals("LOGIN_REQUIRED", assertThrows(KakaoPayException.class,
                () -> payments.readyCart(new MockHttpSession(), true, "csrf")).getCode());
        assertEquals("AUTH_CSRF_INVALID", assertThrows(CartException.class,
                () -> payments.readyCart(session, true, "wrong")).getCode());
        when(carts.checkout(7L)).thenThrow(new CartException("CART_MENU_UNAVAILABLE", 409, "주문 불가"));
        assertThrows(CartException.class, () -> payments.readyCart(session, true, "csrf"));
        verifyNoInteractions(gateway);
    }
}
