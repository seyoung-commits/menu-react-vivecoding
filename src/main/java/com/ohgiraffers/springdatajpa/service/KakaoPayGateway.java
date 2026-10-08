package com.ohgiraffers.springdatajpa.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.ohgiraffers.springdatajpa.exception.KakaoPayException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

@Component
public class KakaoPayGateway {
    private final RestClient client;
    private final String cid;
    private final String secretKey;

    @Autowired
    public KakaoPayGateway(@Value("${kakaopay.cid}") String cid,
                           @Value("${kakaopay.secret-key:}") String secretKey) {
        this(createClient(), cid, secretKey);
    }

    public KakaoPayGateway(RestClient client, String cid, String secretKey) {
        this.client = client;
        this.cid = cid;
        this.secretKey = secretKey;
    }

    private static RestClient createClient() {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl("https://open-api.kakaopay.com")
                .requestFactory(factory).build();
    }

    public void checkConfigured() {
        if (!"TC0ONETIME".equals(cid)) {
            throw new KakaoPayException("KAKAOPAY_TEST_ONLY", 503,
                    "이 학습용 기능은 테스트 CID(TC0ONETIME)만 사용할 수 있습니다.");
        }
        if (secretKey == null || secretKey.isBlank()) {
            throw new KakaoPayException("KAKAOPAY_KEY_MISSING", 503,
                    "Spring 실행 설정에 KAKAOPAY_SECRET_KEY를 추가하고 서버를 다시 실행해 주세요.");
        }
    }

    public ReadyResponse ready(Map<String, Object> payment) {
        ReadyResponse response = post("/online/v1/payment/ready", payment,
                ReadyResponse.class, "KAKAOPAY_READY_FAILED");
        if (response == null || response.tid() == null || response.tid().isBlank()
                || !isHttpsUrl(response.pcUrl()) || !isHttpsUrl(response.mobileUrl())) {
            throw new KakaoPayException("KAKAOPAY_READY_FAILED", 502,
                    "카카오페이에서 올바른 결제 화면 주소를 받지 못했습니다.");
        }
        return response;
    }

    public PaymentResponse approve(Map<String, Object> payment) {
        return post("/online/v1/payment/approve", payment, PaymentResponse.class, "KAKAOPAY_APPROVE_FAILED");
    }

    public PaymentResponse findOrder(String tid) {
        return post("/online/v1/payment/order", Map.of("cid", cid, "tid", tid),
                PaymentResponse.class, "KAKAOPAY_ORDER_FAILED");
    }

    private <T> T post(String path, Map<String, Object> body, Class<T> type, String errorCode) {
        checkConfigured();
        try {
            return client.post().uri(path).header("Authorization", "SECRET_KEY " + secretKey)
                    .contentType(MediaType.APPLICATION_JSON).body(body).retrieve().body(type);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            if (status == 401 || status == 403) {
                throw new KakaoPayException("KAKAOPAY_AUTH_FAILED", 502,
                        "Secret key(dev)와 환경변수 입력 형식을 확인해 주세요.");
            }
            String detail = "KAKAOPAY_READY_FAILED".equals(errorCode)
                    ? "결제 준비 요청이 거절되었습니다. 플랫폼의 사이트 도메인(http://localhost:8080)을 확인해 주세요."
                    : "카카오페이가 요청을 거절했습니다. 결제 상태를 확인해 주세요.";
            // 오류 본문이나 요청 헤더를 출력하지 않는다.
            throw new KakaoPayException(errorCode, 502, detail);
        } catch (RestClientException e) {
            throw new KakaoPayException("KAKAOPAY_UNAVAILABLE", 502,
                    "카카오페이와 통신하지 못했습니다. 네트워크 상태를 확인해 주세요.");
        }
    }

    private boolean isHttpsUrl(String value) {
        if (value == null) return false;
        try {
            URI uri = URI.create(value);
            return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ReadyResponse(String tid,
            @JsonProperty("next_redirect_pc_url") String pcUrl,
            @JsonProperty("next_redirect_mobile_url") String mobileUrl) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record PaymentResponse(String tid, String cid, String status,
            @JsonProperty("partner_order_id") String orderId,
            @JsonProperty("partner_user_id") String userId,
            Integer quantity, Amount amount,
            @JsonProperty("payment_method_type") String paymentMethod,
            @JsonProperty("approved_at") String approvedAt) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Amount(Integer total) {}
}

