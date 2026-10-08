package com.ohgiraffers.springdatajpa.service;

/** 서버가 인증한 우리 회원번호를 보관하는 세션 속성. 요청 본문에서 받지 않는다. */
public final class MemberSession {
    public static final String MEMBER = "LOGIN_MEMBER_CODE";
    private MemberSession() {}

    public static void checkCsrf(jakarta.servlet.http.HttpSession session, String csrf) {
        Object expected;
        try { expected = session == null ? null : session.getAttribute("AUTH_CSRF_TOKEN"); }
        catch (IllegalStateException e) { expected = null; }
        if (!(expected instanceof String token) || token.isBlank() || !java.util.Objects.equals(token, csrf))
            throw new com.ohgiraffers.springdatajpa.exception.CartException("AUTH_CSRF_INVALID", 403,
                    "요청 확인값이 만료됐어요. 새로고침 후 다시 시도해 주세요.");
    }
}
