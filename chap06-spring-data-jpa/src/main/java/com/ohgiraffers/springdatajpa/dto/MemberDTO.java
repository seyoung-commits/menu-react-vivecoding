package com.ohgiraffers.springdatajpa.dto;

import java.time.LocalDateTime;
import java.util.List;

/** 브라우저에는 회원 정보만 전달한다. 키와 토큰은 포함하지 않는다. */
public record MemberDTO(Long memberCode, String nickname, String profileImageUrl, Long kakaoAppId,
        Long kakaoUserId, boolean syncCompleted, LocalDateTime createdAt,
        LocalDateTime lastLoginAt, List<TermDTO> terms) {
    public record TermDTO(String tag, boolean required, boolean agreed, String agreedAt) {}
}
