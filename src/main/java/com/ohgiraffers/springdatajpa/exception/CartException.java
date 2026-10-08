package com.ohgiraffers.springdatajpa.exception;

public class CartException extends RuntimeException {
    private final String code;
    private final int status;
    public CartException(String code, int status, String detail) {
        super(detail);
        this.code = code;
        this.status = status;
    }
    public String getCode() { return code; }
    public int getStatus() { return status; }
}
