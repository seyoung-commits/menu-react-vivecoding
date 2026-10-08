package com.ohgiraffers.springdatajpa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTokenInfoResponse(Long id,
        @JsonProperty("app_id") Long appId,
        @JsonProperty("expires_in") Long expiresIn) {}
