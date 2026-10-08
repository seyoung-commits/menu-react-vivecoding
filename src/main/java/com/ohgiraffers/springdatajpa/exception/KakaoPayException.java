package com.ohgiraffers.springdatajpa.exception;

// 외부 API 오류 본문에는 민감한 정보가 있을 수 있어, 직접 정한 안내만 전달한다.
public class KakaoPayException extends RuntimeException {
    private final String code;
    private final int status;

    public KakaoPayException(String code, int status, String message) {
        super(message);
        this.code = code;
        this.status = status;
    }

    public String getCode() { return code; }
    public int getStatus() { return status; }
}

