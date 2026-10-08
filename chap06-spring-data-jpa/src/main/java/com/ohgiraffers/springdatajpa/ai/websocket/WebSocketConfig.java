package com.ohgiraffers.springdatajpa.ai.websocket;

import com.ohgiraffers.springdatajpa.ai.AiSession;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.http.server.*;
import org.springframework.http.HttpStatus;
import java.util.Map;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {
    private final MenuDraftWebSocketHandler handler;
    public WebSocketConfig(MenuDraftWebSocketHandler handler) { this.handler = handler; }
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(handler, "/ws/ai/menu-draft")
            .setAllowedOrigins("http://localhost:5173")
            .addInterceptors(new HandshakeInterceptor() {
                public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                        WebSocketHandler handler, Map<String, Object> attributes) {
                    if (!(request instanceof ServletServerHttpRequest servlet)) return false;
                    var session = servlet.getServletRequest().getSession(false);
                    try { AiSession.requireLogin(session); }
                    catch (RuntimeException error) { response.setStatusCode(HttpStatus.UNAUTHORIZED); return false; }
                    attributes.put("httpSession", session);
                    return true;
                }
                public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                        WebSocketHandler handler, Exception exception) {}
            });
    }
}
