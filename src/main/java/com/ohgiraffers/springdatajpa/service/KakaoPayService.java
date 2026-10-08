package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.dto.PaymentItem;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.KakaoPayException;
import com.ohgiraffers.springdatajpa.exception.MenuNotFoundException;
import com.ohgiraffers.springdatajpa.repository.MenuRepository;
import com.ohgiraffers.springdatajpa.repository.MemberRepository;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.Serializable;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.List;
import java.util.UUID;

@Service
public class KakaoPayService {
    public static final String PENDING_PAYMENT = "KAKAOPAY_PENDING_PAYMENT";
    public static final String PAYMENT_RESULT = "KAKAOPAY_PAYMENT_RESULT";
    private static final String CART_CLEANED = "KAKAOPAY_CART_CLEANED_ORDER";
    private final MenuRepository menus;
    private final MemberRepository members;
    private final KakaoPayGateway gateway;
    private final CartService carts;
    private final String callbackBaseUrl;

    public KakaoPayService(MenuRepository menus, MemberRepository members, KakaoPayGateway gateway, CartService carts,
                          @Value("${kakaopay.callback-base-url:http://localhost:8080}") String callbackBaseUrl) {
        this.menus = menus;
        this.members = members;
        this.gateway = gateway;
        this.carts = carts;
        this.callbackBaseUrl = callbackBaseUrl;
    }

    public ReadyResult ready(KakaoPayReadyRequest request, HttpSession session) {
        return ready(request, session, false);
    }

    public ReadyResult ready(KakaoPayReadyRequest request, HttpSession session, boolean returnToApp) {
        synchronized (validSession(session)) {
            String userId = paymentUser(session);
            if (request == null || request.menuCode() == null || request.menuCode() < 1) {
                throw new IllegalArgumentException("올바른 메뉴 번호를 입력해 주세요.");
            }
            if (request.quantity() == null || request.quantity() < 1 || request.quantity() > 99) {
                throw new IllegalArgumentException("수량은 1~99 사이로 입력해 주세요.");
            }
            Menu menu = menus.findById(request.menuCode())
                    .orElseThrow(() -> new MenuNotFoundException("해당 메뉴가 존재하지 않습니다."));
            if (!"Y".equals(menu.getOrderableStatus())) {
                throw new IllegalArgumentException("현재 주문할 수 없는 메뉴입니다.");
            }
            long calculatedAmount = (long) menu.getMenuPrice() * request.quantity();
            if (calculatedAmount < 1 || calculatedAmount > Integer.MAX_VALUE) {
                throw new IllegalArgumentException("결제 금액이 허용 범위를 벗어났습니다.");
            }
            return prepare(userId, menu.getMenuCode(), menu.getMenuName(), request.quantity(),
                    (int) calculatedAmount, List.of(new PaymentItem(null, null, menu.getMenuCode(),
                    menu.getMenuName(), menu.getMenuPrice(), request.quantity(), (int) calculatedAmount)),
                    false, session, returnToApp);
        }
    }

    public ReadyResult readyCart(HttpSession session, boolean returnToApp, String csrf) {
        synchronized (validSession(session)) {
            String userId = paymentUser(session);
            MemberSession.checkCsrf(session, csrf);
            Long memberCode = (Long) session.getAttribute(MemberSession.MEMBER);
            CartService.Checkout cart = carts.checkout(memberCode);
            return prepare(userId, 0, cart.name(), cart.quantity(), cart.totalAmount(),
                    cart.items(), true, session, returnToApp);
        }
    }

    private ReadyResult prepare(String userId, int menuCode, String name, int quantity, int totalAmount,
            List<PaymentItem> items, boolean fromCart, HttpSession session, boolean returnToApp) {
        gateway.checkConfigured();
        // 학습용: 세션별 최근 결제 1건. 승인 결과가 불확실하면 새 요청으로 덮어쓰지 않는다.
        PaymentResult previous = result(session);
        if (previous != null && previous.status() == Status.UNKNOWN) {
            throw new KakaoPayException("KAKAOPAY_APPROVAL_UNCERTAIN", 409,
                    "이전 결제 승인 상태를 먼저 조회해 주세요.");
        }
        String orderId = UUID.randomUUID().toString();
        String appQuery = returnToApp ? "&returnToApp=true" : "";
        Map<String, Object> payment = new LinkedHashMap<>();
        payment.put("cid", "TC0ONETIME");
        payment.put("partner_order_id", orderId);
        payment.put("partner_user_id", userId);
        payment.put("item_name", name);
        payment.put("quantity", quantity);
        payment.put("total_amount", totalAmount);
        payment.put("tax_free_amount", 0);
        payment.put("approval_url", callbackBaseUrl + "/api/payments/kakaopay/success?orderId=" + orderId + appQuery);
        payment.put("cancel_url", callbackBaseUrl + "/api/payments/kakaopay/cancel?orderId=" + orderId + appQuery);
        payment.put("fail_url", callbackBaseUrl + "/api/payments/kakaopay/fail?orderId=" + orderId + appQuery);

        KakaoPayGateway.ReadyResponse response = gateway.ready(payment);
        session.setAttribute(PENDING_PAYMENT, new PendingPayment(orderId, userId, response.tid(),
                menuCode, name, quantity, totalAmount, Instant.now(), List.copyOf(items), fromCart));
        session.removeAttribute(PAYMENT_RESULT);
        session.removeAttribute(CART_CLEANED);
        return new ReadyResult(orderId, name, quantity, totalAmount,
                response.pcUrl(), response.mobileUrl());
    }

    public PaymentResult approve(String orderId, String pgToken, HttpSession session) {
        synchronized (validSession(session)) {
            PendingPayment pending = pending(orderId, session);
            PaymentResult saved = result(session);
            if (saved != null) {
                // 새로고침이면 저장한 결과를 돌려준다. 승인 API를 다시 호출하지 않는다.
                if (saved.status() == Status.UNKNOWN) return reconcile(pending, session);
                if (saved.status() == Status.APPROVED) return finishCart(pending, saved, session);
                throw new KakaoPayException("KAKAOPAY_ALREADY_FINISHED", 409,
                        "이미 종료된 결제입니다. 새 결제를 준비해 주세요.");
            }
            if (pgToken == null || pgToken.isBlank() || pgToken.length() > 2048) {
                throw new IllegalArgumentException("카카오페이가 전달한 pg_token이 필요합니다.");
            }
            if (Duration.between(pending.createdAt(), Instant.now()).compareTo(Duration.ofMinutes(30)) > 0) {
                throw new KakaoPayException("KAKAOPAY_SESSION_EXPIRED", 410,
                        "결제 준비 정보가 만료되었습니다. 새 결제를 준비해 주세요.");
            }
            gateway.checkConfigured();
            Map<String, Object> body = Map.of(
                    "cid", "TC0ONETIME", "tid", pending.tid(),
                    "partner_order_id", pending.orderId(), "partner_user_id", pending.userId(),
                    "pg_token", pgToken, "total_amount", pending.totalAmount());

            // 응답 유실 시 실제 승인됐을 수 있으므로, 먼저 UNKNOWN을 기록한다.
            // 토큰은 세션에 저장하지 않는다.
            save(pending, Status.UNKNOWN, null, null, session);
            try {
                var response = gateway.approve(body);
                validateResponse(pending, response, true);
                return save(pending, Status.APPROVED, response.paymentMethod(), response.approvedAt(), session);
            } catch (KakaoPayException e) {
                // 재승인 대신 order API로 확인한다. 확인 불가능하면 UNKNOWN을 유지한다.
                return reconcile(pending, session);
            }
        }
    }

    public PaymentResult status(String orderId, HttpSession session) {
        synchronized (validSession(session)) {
            PendingPayment pending = pending(orderId, session);
            PaymentResult saved = result(session);
            if (saved != null && saved.status() == Status.UNKNOWN) return reconcile(pending, session);
            return saved != null ? finishCart(pending, saved, session) : summary(pending, Status.READY, null, null);
        }
    }

    // 이 cancel은 결제 화면을 닫은 콜백이며, 승인된 결제의 환불 API가 아니다.
    public PaymentResult abort(String orderId, boolean failed, HttpSession session) {
        synchronized (validSession(session)) {
            PendingPayment pending = pending(orderId, session);
            PaymentResult saved = result(session);
            if (saved != null) {
                return saved.status() == Status.UNKNOWN ? reconcile(pending, session) : finishCart(pending, saved, session);
            }
            return save(pending, failed ? Status.FAILED : Status.CANCELED, null, null, session);
        }
    }

    private PaymentResult reconcile(PendingPayment pending, HttpSession session) {
        try {
            var response = gateway.findOrder(pending.tid());
            validateResponse(pending, response, false);
            if ("SUCCESS_PAYMENT".equals(response.status())) {
                validateResponse(pending, response, true);
                return save(pending, Status.APPROVED, response.paymentMethod(), response.approvedAt(), session);
            }
            if ("QUIT_PAYMENT".equals(response.status())) {
                return save(pending, Status.CANCELED, null, null, session);
            }
            if ("FAIL_PAYMENT".equals(response.status()) || "FAIL_AUTH_PASSWORD".equals(response.status())) {
                return save(pending, Status.FAILED, null, null, session);
            }
        } catch (KakaoPayException e) {
            // 상태 조회 자체가 실패했으면 승인 성공/실패를 임의로 결정하지 않는다.
        }
        return save(pending, Status.UNKNOWN, null, null, session);
    }

    private void validateResponse(PendingPayment pending, KakaoPayGateway.PaymentResponse response,
                                  boolean approved) {
        if (response == null || !"TC0ONETIME".equals(response.cid())
                || !pending.tid().equals(response.tid())
                || !pending.orderId().equals(response.orderId())
                || !pending.userId().equals(response.userId())
                || response.quantity() == null || response.quantity() != pending.quantity()
                || response.amount() == null || response.amount().total() == null
                || response.amount().total() != pending.totalAmount()
                || (approved && (response.approvedAt() == null || response.approvedAt().isBlank()
                    || (!"CARD".equals(response.paymentMethod()) && !"MONEY".equals(response.paymentMethod()))))) {
            throw new KakaoPayException("KAKAOPAY_RESPONSE_MISMATCH", 502,
                    "결제 응답이 서버에 저장된 주문 정보와 일치하지 않습니다.");
        }
    }

    private PendingPayment pending(String orderId, HttpSession session) {
        String userId = paymentUser(session);
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("주문 번호가 필요합니다.");
        }
        PendingPayment pending = (PendingPayment) session.getAttribute(PENDING_PAYMENT);
        if (pending == null || !pending.orderId().equals(orderId)) {
            throw new KakaoPayException("KAKAOPAY_SESSION_NOT_FOUND", 409,
                    "이 브라우저의 최근 결제 정보를 찾지 못했습니다. 같은 브라우저에서 새 결제를 준비해 주세요.");
        }
        if (!userId.equals(pending.userId())) {
            throw new KakaoPayException("PAYMENT_FORBIDDEN", 403,
                    "이 결제를 시작한 회원으로 로그인해 주세요.");
        }
        return pending;
    }

    private HttpSession validSession(HttpSession session) {
        if (session == null) throw loginRequired();
        return session;
    }

    private String paymentUser(HttpSession session) {
        Object value;
        try { value = session.getAttribute(MemberSession.MEMBER); }
        catch (IllegalStateException e) { throw loginRequired(); }
        if (!(value instanceof Long memberCode) || memberCode < 1 || !members.existsById(memberCode))
            throw loginRequired();
        return "member-" + memberCode;
    }

    private KakaoPayException loginRequired() {
        return new KakaoPayException("LOGIN_REQUIRED", 401, "결제하려면 먼저 로그인해 주세요.");
    }

    private PaymentResult result(HttpSession session) {
        return (PaymentResult) session.getAttribute(PAYMENT_RESULT);
    }

    private PaymentResult save(PendingPayment pending, Status status, String method,
                               String approvedAt, HttpSession session) {
        PaymentResult result = summary(pending, status, method, approvedAt);
        session.setAttribute(PAYMENT_RESULT, result);
        return finishCart(pending, result, session);
    }

    private PaymentResult finishCart(PendingPayment pending, PaymentResult result, HttpSession session) {
        if (result.status() != Status.APPROVED || !pending.fromCart()
                || pending.orderId().equals(session.getAttribute(CART_CLEANED))) return result;
        boolean cleanupPending = false;
        try {
            Long memberCode = (Long) session.getAttribute(MemberSession.MEMBER);
            carts.completePurchase(memberCode, pending.items());
            session.setAttribute(CART_CLEANED, pending.orderId());
        } catch (RuntimeException e) {
            // 결제 승인은 이미 성공했다. 정리 실패를 결제 실패로 바꾸거나 재승인하지 않는다.
            cleanupPending = true;
        }
        PaymentResult updated = new PaymentResult(result.orderId(), result.status(), result.menuName(),
                result.quantity(), result.totalAmount(), result.paymentMethod(), result.approvedAt(),
                result.items(), cleanupPending);
        session.setAttribute(PAYMENT_RESULT, updated);
        return updated;
    }

    private PaymentResult summary(PendingPayment pending, Status status, String method, String approvedAt) {
        return new PaymentResult(pending.orderId(), status, pending.menuName(),
                pending.quantity(), pending.totalAmount(), method, approvedAt, pending.items(), false);
    }

    public enum Status { READY, APPROVED, CANCELED, FAILED, UNKNOWN }
    public record ReadyResult(String orderId, String menuName, int quantity, int totalAmount,
                              String redirectUrl, String mobileRedirectUrl) {}
    public record PendingPayment(String orderId, String userId, String tid, int menuCode,
                                 String menuName, int quantity, int totalAmount, Instant createdAt,
                                 List<PaymentItem> items, boolean fromCart) implements Serializable {
        public PendingPayment(String orderId, String userId, String tid, int menuCode,
                String menuName, int quantity, int totalAmount, Instant createdAt) {
            this(orderId, userId, tid, menuCode, menuName, quantity, totalAmount, createdAt,
                    List.of(new PaymentItem(null, null, menuCode, menuName,
                            totalAmount / quantity, quantity, totalAmount)), false);
        }
    }
    public record PaymentResult(String orderId, Status status, String menuName, int quantity,
                                int totalAmount, String paymentMethod, String approvedAt,
                                List<PaymentItem> items, boolean cartCleanupPending)
            implements Serializable {}
}

