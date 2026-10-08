package com.ohgiraffers.springdatajpa.ai.controller;

import com.ohgiraffers.springdatajpa.ai.AiSession;
import com.ohgiraffers.springdatajpa.ai.dto.RecommendationRequest;
import com.ohgiraffers.springdatajpa.ai.service.RecommendationService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import io.swagger.v3.oas.annotations.Operation;

@RestController
@RequestMapping("/api/ai")
public class RecommendationController {
    private final RecommendationService service;
    public RecommendationController(RecommendationService service) { this.service = service; }
    @Operation(summary = "AI 메뉴 추천", description = "로그인 세션과 X-CSRF-Token 필요. SSE 이벤트: filter, candidates, delta, recommended, done, error.")
    @PostMapping(value = "/recommendations", produces = "text/event-stream")
    public SseEmitter recommend(HttpServletRequest request,
            @RequestHeader(value = "X-CSRF-Token", required = false) String csrf,
            @Valid @RequestBody RecommendationRequest body) {
        AiSession.require(request.getSession(false), csrf);
        if (!"user".equals(body.getMessages().get(body.getMessages().size() - 1).getRole()))
            throw new IllegalArgumentException("마지막 메시지는 사용자 질문이어야 합니다.");
        return service.recommend(body.getMessages());
    }
}
