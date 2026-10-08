package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.common.*;
import com.ohgiraffers.springdatajpa.dto.MemberDTO;
import com.ohgiraffers.springdatajpa.exception.KakaoAuthException;
import com.ohgiraffers.springdatajpa.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.util.UriComponentsBuilder;
import java.net.URI;
import java.util.*;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "카카오 로그인", description = "회원 저장, 세션 로그인, 로그아웃, 카카오싱크 약관 확인")
public class KakaoLoginController {
    public static final String ATTEMPT = "KAKAO_LOGIN_ATTEMPT";
    public static final String MEMBER = MemberSession.MEMBER;
    public static final String CSRF = "AUTH_CSRF_TOKEN";
    private final String restApiKey;
    private final String redirectUri;
    private final String frontendBase;
    private final boolean syncEnabled;
    private final KakaoLoginService kakao;
    private final MemberService members;

    public KakaoLoginController(@Value("${kakao.rest-api-key:}") String restApiKey,
            @Value("${kakao.redirect-uri}") String redirectUri,
            @Value("${kakao.frontend-base-url:http://localhost:5173}") String frontendBase,
            @Value("${kakao.sync-enabled:false}") boolean syncEnabled,
            KakaoLoginService kakao, MemberService members) {
        this.restApiKey = restApiKey;
        this.redirectUri = redirectUri;
        this.frontendBase = frontendBase.replaceAll("/+$", "");
        this.syncEnabled = syncEnabled;
        this.kakao = kakao;
        this.members = members;
    }

    public record LoginAttempt(String state, long startedAt, boolean sync) {}

    @GetMapping("/kakao/login")
    @Operation(summary = "카카오 로그인 시작", description = "브라우저에서 직접 접속. prompt=login으로 재인증하며 닉네임·사진의 추가 동의를 요청한다. 현재 테스트 앱은 OpenID Connect 활성화 상태다.")
    public ResponseEntity<Void> login(HttpServletRequest request) {
        kakao.checkConfigured();
        HttpSession session = request.getSession(true);
        String state = UUID.randomUUID().toString();
        synchronized (session) {
            session.setAttribute(ATTEMPT, new LoginAttempt(state, System.currentTimeMillis(), syncEnabled));
        }
        URI uri = UriComponentsBuilder.fromUriString("https://kauth.kakao.com/oauth/authorize")
                .queryParam("response_type", "code").queryParam("client_id", restApiKey)
                .queryParam("redirect_uri", redirectUri).queryParam("state", state)
                // 카카오 계정 세션이 남아 있어도 로그인 화면을 생략하지 않는다.
                .queryParam("prompt", "login")
                // 기존 가입자도 새 프로필 항목에 동의할 수 있다. 현재 앱은 OIDC가 켜져 있다.
                .queryParam("scope", "profile_nickname,profile_image,openid")
                .build().encode().toUri();
        return ResponseEntity.status(HttpStatus.FOUND).headers(privateHeaders()).location(uri).build();
    }

    @GetMapping("/kakao/callback")
    @Operation(summary = "카카오 로그인 콜백", description = "카카오가 호출. state 검사 후 회원을 저장하고 React 결과 화면으로 303 이동.")
    public ResponseEntity<Void> callback(
            @RequestParam(name = "code", required = false) String code,
            @RequestParam(name = "state", required = false) String state,
            @RequestParam(name = "error", required = false) String error,
            HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        if (session == null) return result("SESSION_EXPIRED");
        LoginAttempt attempt;
        try {
            synchronized (session) {
                attempt = (LoginAttempt) session.getAttribute(ATTEMPT);
                session.removeAttribute(ATTEMPT); // 일회용: 중복 콜백은 토큰 요청을 다시 하지 않는다.
            }
        } catch (IllegalStateException e) { return result("SESSION_EXPIRED"); }
        if (attempt == null || state == null || !attempt.state().equals(state))
            return result("INVALID_STATE");
        long elapsed = System.currentTimeMillis() - attempt.startedAt();
        if (elapsed < 0 || elapsed > 600_000) return result("SESSION_EXPIRED");
        if (error != null) return result("access_denied".equals(error) ? "CANCELED" : "FAILED");
        if (code == null || code.isBlank()) return result("FAILED");
        try {
            var token = kakao.requestToken(code);
            var info = kakao.requestTokenInfo(token.accessToken());
            var user = kakao.requestUser(token.accessToken());
            if (!Objects.equals(info.id(), user.id())) return result("FAILED");
            var consent = attempt.sync() ? kakao.requestTerms(token.accessToken(), user.id())
                    : List.<com.ohgiraffers.springdatajpa.dto.KakaoTermsResponse.ServiceTerm>of();
            MemberDTO member = members.login(info.appId(), user, consent, attempt.sync());
            // 로그인 전 세션 ID를 교체한다. 기존 결제 연습의 세션 속성은 보존한다.
            synchronized (session) {
                request.changeSessionId();
                session.setAttribute(MEMBER, member.memberCode());
                session.setAttribute(CSRF, UUID.randomUUID().toString());
                session.setMaxInactiveInterval(1800);
            }
            return result("SUCCESS");
        } catch (KakaoAuthException e) {
            return result("SYNC_TERMS_REQUIRED".equals(e.getCode()) ? "TERMS_REQUIRED" : "FAILED");
        } catch (RuntimeException e) {
            // DB/통신 예외의 원문, 인가 코드, 토큰을 URL이나 응답에 넣지 않는다.
            return result("FAILED");
        }
    }

    @GetMapping("/me")
    @Operation(summary = "현재 로그인 회원 조회", description = "result: authenticated, user, csrfToken, syncEnabled. user에는 nickname과 profileImageUrl(사진이 없으면 null)을 포함한다. 세션 쿠키 필요.")
    public ResponseEntity<ResponseMessage> me(HttpServletRequest request) {
        HttpSession session = request.getSession(false);
        MemberDTO user = null;
        String csrf = null;
        if (session != null) {
            Long id = (Long) session.getAttribute(MEMBER);
            if (id != null) {
                user = members.find(id).orElse(null);
                if (user != null) csrf = (String) session.getAttribute(CSRF);
                else {
                    session.removeAttribute(MEMBER);
                    session.removeAttribute(CSRF);
                }
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("authenticated", user != null);
        data.put("user", user);
        data.put("csrfToken", csrf);
        data.put("syncEnabled", syncEnabled);
        return ResponseEntity.ok().headers(privateHeaders())
                .body(new ResponseMessage(200, "로그인 상태 조회 성공", data));
    }

    @PostMapping("/logout")
    @Operation(summary = "우리 사이트 로그아웃", description = "세션 쿠키와 /me의 csrfToken을 X-CSRF-Token 헤더로 전달. 카카오 계정 자체는 로그아웃하지 않음.")
    public ResponseEntity<ResponseMessage> logout(HttpServletRequest request,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            synchronized (session) {
                String expected = (String) session.getAttribute(CSRF);
                if (expected != null && !Objects.equals(expected, csrf))
                    throw new KakaoAuthException("AUTH_CSRF_INVALID", 403, "로그아웃 확인값이 일치하지 않습니다.");
                String origin = request.getHeader("Origin");
                String backendOrigin = origin(redirectUri);
                if (origin != null && !origin.equals(origin(frontendBase)) && !origin.equals(backendOrigin))
                    throw new KakaoAuthException("AUTH_ORIGIN_INVALID", 403, "허용되지 않은 요청입니다.");
                session.invalidate();
            }
        }
        return ResponseEntity.ok().headers(privateHeaders())
                .body(new ResponseMessage(200, "로그아웃 성공", Map.of("authenticated", false)));
    }

    @ExceptionHandler(KakaoAuthException.class)
    public ResponseEntity<ErrorResponse> authError(KakaoAuthException e) {
        return ResponseEntity.status(e.getStatus()).headers(privateHeaders())
                .body(new ErrorResponse(e.getCode(), "카카오 로그인 요청 실패", e.getMessage()));
    }

    private ResponseEntity<Void> result(String status) {
        return ResponseEntity.status(HttpStatus.SEE_OTHER).headers(privateHeaders())
                .location(URI.create(frontendBase + "/auth/result?status=" + status)).build();
    }
    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<ErrorResponse> unexpectedAuthError(RuntimeException e) {
        return ResponseEntity.internalServerError().headers(privateHeaders())
                .body(new ErrorResponse("AUTH_SERVER_ERROR", "로그인 정보를 처리하지 못했습니다.",
                        "잠시 후 다시 시도해 주세요."));
    }
    private static HttpHeaders privateHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setCacheControl("no-store");
        headers.set("Referrer-Policy", "no-referrer");
        return headers;
    }
    private static String origin(String url) {
        URI uri = URI.create(url);
        return uri.getScheme() + "://" + uri.getAuthority();
    }
}
