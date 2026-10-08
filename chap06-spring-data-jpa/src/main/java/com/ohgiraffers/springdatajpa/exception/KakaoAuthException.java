package com.ohgiraffers.springdatajpa.exception;

/** 원본 카카오 응답과 토큰 대신, 안전한 안내 문구만 전달한다. */
public class KakaoAuthException extends RuntimeException {
    private final String code;
    private final int status;
    public KakaoAuthException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}
