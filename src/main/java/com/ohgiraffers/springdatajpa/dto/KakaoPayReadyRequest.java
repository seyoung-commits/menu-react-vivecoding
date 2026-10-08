package com.ohgiraffers.springdatajpa.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record KakaoPayReadyRequest(
        @Schema(description = "주문 가능한 메뉴 번호", example = "1") Integer menuCode,
        @Schema(description = "수량 (1~99)", example = "1") Integer quantity) {
}

