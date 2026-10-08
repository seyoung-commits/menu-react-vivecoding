package com.ohgiraffers.springdatajpa.dto;

import java.io.Serializable;

/** 결제 준비 시 확정한 항목·수량·가격. cart 필드는 단일 메뉴 바로 결제에서 null이다. */
public record PaymentItem(Long cartItemCode, Long cartVersion, int menuCode,
        String menuName, int unitPrice, int quantity, int totalAmount) implements Serializable {}
