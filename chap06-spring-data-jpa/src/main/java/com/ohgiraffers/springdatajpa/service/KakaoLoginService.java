package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.exception.KakaoAuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.web.client.*;
import java.net.http.HttpClient;
import java.time.*;
import java.util.*;

@Service
public class KakaoLoginService {
    private final String restApiKey;
    private final String clientSecret;
    private final String redirectUri;
    private final Set<String> requiredTerms;
    private final RestClient tokenClient;
    private final RestClient apiClient;

    @Autowired
    public KakaoLoginService(@Value("${kakao.rest-api-key:}") String restApiKey,
            @Value("${kakao.client-secret:}") String clientSecret,
            @Value("${kakao.redirect-uri}") String redirectUri,
            @Value("${kakao.sync-required-terms:menu_terms_20261005}") String requiredTerms) {
        this(restApiKey, clientSecret, redirectUri, requiredTerms,
                createClient("https://kauth.kakao.com"), createClient("https://kapi.kakao.com"));
    }

    public KakaoLoginService(String restApiKey, String clientSecret, String redirectUri,
            String requiredTerms, RestClient tokenClient, RestClient apiClient) {
        this.restApiKey = restApiKey;
        this.clientSecret = clientSecret;
        this.redirectUri = redirectUri;
        this.requiredTerms = new HashSet<>();
        Arrays.stream(requiredTerms.split(",")).map(String::strip).filter(s -> !s.isEmpty())
                .forEach(this.requiredTerms::add);
        this.tokenClient = tokenClient;
        this.apiClient = apiClient;
    }

    private static RestClient createClient(String base) {
        HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(http);
        factory.setReadTimeout(Duration.ofSeconds(15));
        return RestClient.builder().baseUrl(base).requestFactory(factory).build();
    }

    public void checkConfigured() {
        if (restApiKey.isBlank() || clientSecret.isBlank()) {
            throw new KakaoAuthException("AUTH_CONFIGURATION", 503,
                    "Spring 실행 설정에 카카오 로그인 키를 넣고 다시 실행해 주세요.");
        }
    }

    public KakaoTokenResponse requestToken(String code) {
        checkConfigured();
        if (code == null || code.isBlank()) throw new KakaoAuthException(
                "AUTH_CODE_MISSING", 400, "인가 코드가 없습니다. 다시 로그인해 주세요.");
        var form = new LinkedMultiValueMap<String, String>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", restApiKey);
        form.add("client_secret", clientSecret);
        form.add("redirect_uri", redirectUri);
        form.add("code", code);
        try {
            var token = tokenClient.post().uri("/oauth/token")
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED).body(form)
                    .retrieve().body(KakaoTokenResponse.class);
            if (token == null || token.accessToken() == null || token.accessToken().isBlank())
                throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502,
                        "카카오 토큰 응답을 확인하지 못했습니다.");
            return token;
        } catch (RestClientException e) {
            // e.getMessage()에는 응답 원문이 들어갈 수 있어 브라우저로 전달하지 않는다.
            throw new KakaoAuthException("AUTH_TOKEN_FAILED", 502,
                    "카카오 토큰 발급에 실패했습니다. 새 로그인 요청으로 다시 시도해 주세요.");
        }
    }

    private <T> T getApi(String path, String token, Class<T> type) {
        if (token == null || token.isBlank()) throw new KakaoAuthException(
                "AUTH_TOKEN_MISSING", 400, "액세스 토큰이 없습니다.");
        try {
            T result = apiClient.get().uri(path)
                    .headers(h -> h.setBearerAuth(token))
                    .retrieve().body(type);
            if (result == null) throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502,
                    "카카오 응답을 확인하지 못했습니다.");
            return result;
        } catch (RestClientException e) {
            throw new KakaoAuthException("AUTH_PROVIDER_FAILED", 502,
                    "카카오 사용자 정보를 확인하지 못했습니다. 다시 시도해 주세요.");
        }
    }

    public KakaoUserResponse requestUser(String token) {
        var user = getApi("/v2/user/me", token, KakaoUserResponse.class);
        if (user.id() == null || user.id() <= 0) throw new KakaoAuthException(
                "AUTH_PROVIDER_RESPONSE", 502, "카카오 회원번호를 확인하지 못했습니다.");
        return user;
    }

    /** 카카오가 확인한 앱 ID를 사용하므로 테스트 앱도 별도 회원으로 구분된다. */
    public KakaoTokenInfoResponse requestTokenInfo(String token) {
        var info = getApi("/v1/user/access_token_info", token, KakaoTokenInfoResponse.class);
        if (info.id() == null || info.appId() == null || info.appId() <= 0
                || info.expiresIn() == null || info.expiresIn() <= 0)
            throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502,
                    "카카오 토큰이 유효하지 않습니다.");
        return info;
    }

    public List<KakaoTermsResponse.ServiceTerm> requestTerms(String token, Long userId) {
        var response = getApi("/v2/user/service_terms?result=app_service_terms", token,
                KakaoTermsResponse.class);
        if (!Objects.equals(userId, response.id())) throw new KakaoAuthException(
                "AUTH_PROVIDER_RESPONSE", 502, "약관 동의의 회원번호가 일치하지 않습니다.");
        var terms = response.serviceTerms() == null ? List.<KakaoTermsResponse.ServiceTerm>of()
                : response.serviceTerms();
        if (requiredTerms.isEmpty()) throw new KakaoAuthException("SYNC_CONFIGURATION", 503,
                "필수 약관 태그를 설정해 주세요.");
        Set<String> agreed = new HashSet<>();
        for (var term : terms) {
            if (term.tag() == null || term.tag().length() > 100
                    || !term.tag().matches("[A-Za-z0-9_-]+"))
                throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502, "약관 태그가 올바르지 않습니다.");
            if (Boolean.TRUE.equals(term.required()) && !Boolean.TRUE.equals(term.agreed()))
                throw new KakaoAuthException("SYNC_TERMS_REQUIRED", 403, "필수 약관에 동의해 주세요.");
            if (Boolean.TRUE.equals(term.agreed())) {
                if (term.agreedAt() == null || term.agreedAt().length() > 40)
                    throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502, "약관 동의 시각이 없습니다.");
                try { Instant.parse(term.agreedAt()); }
                catch (DateTimeException e) { throw new KakaoAuthException("AUTH_PROVIDER_RESPONSE", 502,
                        "약관 동의 시각이 올바르지 않습니다."); }
                agreed.add(term.tag());
            }
        }
        if (!agreed.containsAll(requiredTerms)) throw new KakaoAuthException(
                "SYNC_TERMS_REQUIRED", 403, "필수 약관에 동의해 주세요.");
        return List.copyOf(terms);
    }
}
