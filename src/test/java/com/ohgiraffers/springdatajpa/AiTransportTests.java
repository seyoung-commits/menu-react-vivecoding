package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.ai.client.OpenAiClient;
import com.ohgiraffers.springdatajpa.dto.MenuDTO;
import com.ohgiraffers.springdatajpa.service.MenuService;
import com.ohgiraffers.springdatajpa.service.MemberSession;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.web.bind.annotation.*;
import tools.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(AiTransportTests.Config.class)
class AiTransportTests {
    @LocalServerPort int port;
    @Autowired ObjectMapper mapper;
    @MockitoBean OpenAiClient ai;
    @MockitoBean MenuService menus;
    HttpClient http = HttpClient.newHttpClient();
    String base() { return "http://localhost:" + port; }

    // 테스트 컨텍스트에만 존재한다. 운영 애플리케이션에는 로그인 우회 주소가 등록되지 않는다.
    @TestConfiguration static class Config {
        @Bean SessionEndpoint testSessionEndpoint() { return new SessionEndpoint(); }
    }
    @RestController static class SessionEndpoint {
        @GetMapping("/__ai_test/session")
        Map<String, String> session(HttpServletRequest request) {
            var session = request.getSession(true);
            session.setAttribute(MemberSession.MEMBER, 1L);
            session.setAttribute("AUTH_CSRF_TOKEN", "transport-csrf");
            return Map.of("status", "ok");
        }
    }
    String cookie() throws Exception {
        var response = http.send(HttpRequest.newBuilder(URI.create(base() + "/__ai_test/session")).GET().build(), HttpResponse.BodyHandlers.ofString());
        return response.headers().firstValue("set-cookie").orElseThrow().split(";", 2)[0];
    }
    HttpResponse<String> recommend(String cookie, String csrf) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create(base() + "/api/ai/recommendations"))
            .header("Content-Type", "application/json").header("Accept", "text/event-stream, application/json")
            .header("Origin", "http://localhost:5173").header("X-CSRF-Token", csrf)
            .POST(HttpRequest.BodyPublishers.ofString("{\"messages\":[{\"role\":\"user\",\"content\":\"추천해줘\"}]}"));
        if (cookie != null) builder.header("Cookie", cookie);
        return http.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }
    @BeforeEach @SuppressWarnings("unchecked") void response() {
        when(ai.isEnabled()).thenReturn(true);
        when(menus.findAllMenus()).thenReturn(List.of(new MenuDTO(7, "테스트 메뉴", 4500, 1, "한식", "Y")));
        when(ai.createJson(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyMap()))
            .thenReturn(mapper.readTree("{\"maxPrice\":5000,\"menuCodes\":[7]}"));
        doAnswer(call -> { ((Consumer<String>) call.getArgument(5)).accept("테스트 메뉴를 추천해요."); return null; })
            .when(ai).streamText(anyString(), anyString(), anyString(), anyList(), anyInt(), any());
    }
    @Test void sseAuthenticatesSessionAndStreamsRealMenuCards() throws Exception {
        var response = recommend(cookie(), "transport-csrf");
        assertEquals(200, response.statusCode());
        assertEquals("http://localhost:5173", response.headers().firstValue("access-control-allow-origin").orElseThrow());
        assertTrue(response.body().contains("event:delta"));
        assertTrue(response.body().contains("event:recommended"));
        assertTrue(response.body().contains("\"menuCode\":7"));
        assertTrue(response.body().contains("event:done"));
    }
    @Test void sseRejectsMissingLoginAndWrongCsrfBeforeCallingAi() throws Exception {
        assertEquals(401, recommend(null, "transport-csrf").statusCode());
        assertEquals(403, recommend(cookie(), "wrong").statusCode());
        verify(ai, never()).createJson(anyString(), anyString(), anyString(), anyList(), anyInt(), anyString(), anyMap());
    }
    @Test void websocketAuthenticatesAndStreamsDescription() throws Exception {
        var messages = new LinkedBlockingQueue<String>();
        WebSocket socket = http.newWebSocketBuilder().header("Cookie", cookie()).header("Origin", "http://localhost:5173")
            .buildAsync(URI.create("ws://localhost:" + port + "/ws/ai/menu-draft"), new WebSocket.Listener() {
                StringBuilder buffer = new StringBuilder();
                public void onOpen(WebSocket socket) { socket.request(1); }
                public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
                    buffer.append(data);
                    if (last) { messages.add(buffer.toString()); buffer.setLength(0); }
                    socket.request(1); return null;
                }
            }).get(10, TimeUnit.SECONDS);
        try {
            socket.sendText("{\"type\":\"auth\",\"csrfToken\":\"transport-csrf\"}", true).join();
            assertTrue(messages.poll(10, TimeUnit.SECONDS).contains("auth_ok"));
            socket.sendText("{\"type\":\"draft\",\"menuName\":\"테스트 메뉴\"}", true).join();
            assertTrue(messages.poll(10, TimeUnit.SECONDS).contains("delta"));
            assertTrue(messages.poll(10, TimeUnit.SECONDS).contains("done"));
        } finally { socket.sendClose(WebSocket.NORMAL_CLOSURE, "test complete").join(); }
    }
}
