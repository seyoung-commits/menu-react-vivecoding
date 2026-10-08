package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.common.ResponseMessage;
import com.ohgiraffers.springdatajpa.dto.KakaoPayReadyRequest;
import com.ohgiraffers.springdatajpa.exception.KakaoPayException;
import com.ohgiraffers.springdatajpa.service.KakaoPayService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.Map;
import java.util.function.Supplier;

@Tag(name = "카카오페이 테스트 결제", description = "학습용 단건 결제")
@RestController
@RequestMapping("/api/payments/kakaopay")
public class KakaoPayController {
    private final KakaoPayService payments;
    private final String frontendBaseUrl;

    public KakaoPayController(KakaoPayService payments) {
        this(payments, "http://localhost:5173");
    }

    @Autowired
    public KakaoPayController(KakaoPayService payments,
            @Value("${kakaopay.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        this.payments = payments;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @Operation(summary = "테스트 결제 준비",
            description = "로그인 필수(비로그인 401 LOGIN_REQUIRED). 주문자는 서버 세션의 회원번호로 식별합니다. 메뉴 번호와 수량만 받으며 금액은 DB 가격으로 계산합니다. result.payment에 orderId, menuName, quantity, totalAmount, redirectUrl, mobileRedirectUrl을 반환합니다. 가장 최근 결제 1건만 세션에 보관하며 서버 재시작 시 초기화됩니다.")
    @PostMapping(value = "/ready", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseMessage> ready(@RequestBody KakaoPayReadyRequest request,
                                 @RequestParam(defaultValue = "false") boolean returnToApp,
                                 @Parameter(hidden = true) HttpSession session) {
        return response("카카오페이 테스트 결제 준비 성공", payments.ready(request, session, returnToApp));
    }

    @Operation(summary = "결제 인증 완료 콜백",
            description = "카카오페이가 인증 후 orderId와 pg_token을 붙여 이동시키는 주소입니다. 주문 번호·수량·총액을 검증하여 최종 승인하며, 장바구니 결제는 승인 성공 후 준비 당시와 동일한 항목만 정리합니다. 새로고침으로 재승인하지 않습니다.")
    @GetMapping("/success")
    public ResponseEntity<?> success(
            @RequestParam(required = false) String orderId,
            @RequestParam(name = "pg_token", required = false) String pgToken,
            @RequestParam(defaultValue = "false") boolean returnToApp,
            @Parameter(hidden = true) HttpSession session) {
        return callback(orderId, returnToApp, () -> payments.approve(orderId, pgToken, session));
    }

    @PostMapping("/cart/ready")
    @Operation(summary = "내 장바구니 묶음 결제 준비", description = "로그인과 X-CSRF-Token 필요. 서버가 장바구니 전체와 현재 DB 가격·주문 가능 상태를 조회해 총액을 확정합니다. 클라이언트의 금액·회원번호를 받지 않습니다. result.payment에 orderId, menuName, quantity, totalAmount, redirectUrl, mobileRedirectUrl 반환. 승인·상태 결과의 items에 준비 당시 항목별 가격·수량을 반환합니다.")
    public ResponseEntity<ResponseMessage> readyCart(@RequestParam(defaultValue = "false") boolean returnToApp,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf,
            @Parameter(hidden = true) HttpSession session) {
        return response("장바구니 테스트 결제 준비 성공", payments.readyCart(session, returnToApp, csrf));
    }

    @Operation(summary = "결제 화면 취소 콜백", description = "인증 진행을 중단했을 때의 콜백입니다. 결제 완료 후 환불하는 API는 아닙니다.")
    @GetMapping("/cancel")
    public ResponseEntity<?> cancel(@RequestParam(required = false) String orderId,
                                                  @RequestParam(defaultValue = "false") boolean returnToApp,
                                                  @Parameter(hidden = true) HttpSession session) {
        return callback(orderId, returnToApp, () -> payments.abort(orderId, false, session));
    }

    @Operation(summary = "결제 인증 실패 콜백")
    @GetMapping("/fail")
    public ResponseEntity<?> fail(@RequestParam(required = false) String orderId,
                                                @RequestParam(defaultValue = "false") boolean returnToApp,
                                                @Parameter(hidden = true) HttpSession session) {
        return callback(orderId, returnToApp, () -> payments.abort(orderId, true, session));
    }

    @Operation(summary = "최근 테스트 결제 상태 조회",
            description = "로그인 필수(비로그인 401 LOGIN_REQUIRED). 같은 브라우저 세션에서 해당 회원이 시작한 결제만 조회합니다(다른 회원 403 PAYMENT_FORBIDDEN). UNKNOWN이면 실제 상태를 확인하며 승인 요청을 반복하지 않습니다. result.payment.status는 READY, APPROVED, CANCELED, FAILED, UNKNOWN 중 하나입니다.")
    @GetMapping("/status")
    public ResponseEntity<ResponseMessage> status(@RequestParam(required = false) String orderId,
                                                  @Parameter(hidden = true) HttpSession session) {
        return outcome(payments.status(orderId, session));
    }

    private ResponseEntity<ResponseMessage> outcome(KakaoPayService.PaymentResult payment) {
        String message = switch (payment.status()) {
            case READY -> "카카오페이 테스트 결제 준비 상태";
            case APPROVED -> "카카오페이 테스트 결제 승인 완료";
            case CANCELED -> "카카오페이 테스트 결제 진행 취소";
            case FAILED -> "카카오페이 테스트 결제 실패";
            case UNKNOWN -> "결제 승인 상태 확인이 필요합니다. 잠시 후 상태를 다시 조회해 주세요.";
        };
        return response(message, payment);
    }

    private ResponseEntity<?> callback(String orderId, boolean returnToApp,
                                       Supplier<KakaoPayService.PaymentResult> action) {
        try {
            KakaoPayService.PaymentResult payment = action.get();
            return returnToApp ? redirectToApp(payment.orderId()) : outcome(payment);
        } catch (KakaoPayException | IllegalArgumentException e) {
            // 앱에서 시작했다면 오류도 결과 화면에서 status API로 확인한다.
            if (returnToApp) return redirectToApp(orderId);
            throw e;
        }
    }

    private ResponseEntity<Void> redirectToApp(String orderId) {
        // 클라이언트가 임의의 이동 주소를 지정할 수 없도록 서버 설정만 사용한다.
        var location = UriComponentsBuilder.fromUriString(frontendBaseUrl)
                .path("/payments/result").queryParam("orderId", orderId == null ? "" : orderId)
                .build().encode().toUri();
        return ResponseEntity.status(303).cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer").location(location).build();
    }

    private ResponseEntity<ResponseMessage> response(String message, Object payment) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .header("Referrer-Policy", "no-referrer")
                .body(new ResponseMessage(200, message, Map.of("payment", payment)));
    }
}

