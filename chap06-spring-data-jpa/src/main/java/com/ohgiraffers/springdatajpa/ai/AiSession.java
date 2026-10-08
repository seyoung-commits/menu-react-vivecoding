package com.ohgiraffers.springdatajpa.ai;

import jakarta.servlet.http.HttpSession;
import com.ohgiraffers.springdatajpa.service.MemberSession;
import com.ohgiraffers.springdatajpa.exception.CartException;

/** 기존 카카오 로그인 세션과 CSRF 확인값으로 AI 요청을 인증한다. */
public final class AiSession {
    private AiSession() {}
    public static void requireLogin(HttpSession session) {
        try {
            if (session != null && session.getAttribute(MemberSession.MEMBER) instanceof Long) return;
        } catch (IllegalStateException ignored) {}
        throw new CartException("AI_LOGIN_REQUIRED", 401, "카카오 로그인 후 AI 기능을 사용할 수 있어요.");
    }
    public static void require(HttpSession session, String csrf) {
        requireLogin(session);
        MemberSession.checkCsrf(session, csrf);
    }
}
