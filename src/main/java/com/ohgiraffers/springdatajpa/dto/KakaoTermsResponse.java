package com.ohgiraffers.springdatajpa.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record KakaoTermsResponse(Long id,
        @JsonProperty("service_terms") List<ServiceTerm> serviceTerms) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ServiceTerm(String tag, Boolean required, Boolean agreed,
            @JsonProperty("agreed_at") String agreedAt) {}
}
