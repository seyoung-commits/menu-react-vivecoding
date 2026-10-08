package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.ai.AiSession;
import com.ohgiraffers.springdatajpa.ai.client.OpenAiClient;
import com.ohgiraffers.springdatajpa.ai.service.RecommendationService;
import com.ohgiraffers.springdatajpa.ai.websocket.MenuDraftWebSocketHandler;
import com.ohgiraffers.springdatajpa.dto.MenuDTO;
import com.ohgiraffers.springdatajpa.repository.CategoryRepository;
import com.ohgiraffers.springdatajpa.service.*;
import com.ohgiraffers.springdatajpa.exception.CartException;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.socket.*;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@SpringBootTest
@Transactional
class AiMigrationTests {
    @Autowired MenuService menus;
    @Autowired CategoryRepository categories;
    @Autowired MenuDraftWebSocketHandler socketHandler;
    @Autowired ObjectMapper mapper;
    @MockitoBean OpenAiClient ai;

    @Test void savesAiFieldsAndPreservesThemForLegacyUpdates() {
        int category = categories.findAll().get(0).getCategoryCode();
        MenuDTO request = new MenuDTO(0, "AI 이식 테스트", 5500, category, null, "Y");
        request.setMenuIngredients("쌀, 달걀");
        request.setMenuDescription("달걀을 곁들인 메뉴다.");
        var saved = menus.saveMenu(request);
        var loaded = menus.findMenuByCode(saved.getMenuCode());
        assertEquals("쌀, 달걀", loaded.getMenuIngredients());
        assertEquals("달걀을 곁들인 메뉴다.", loaded.getMenuDescription());
        var legacy = new MenuDTO(0, "이름 수정", 6000, category, null, "Y");
        loaded = menus.updateMenu(saved.getMenuCode(), legacy);
        assertEquals("달걀을 곁들인 메뉴다.", loaded.getMenuDescription());
        legacy.setMenuDescription("");
        assertEquals("", menus.updateMenu(saved.getMenuCode(), legacy).getMenuDescription());
    }

    @Test void validatesRealCodesPriceAvailabilityAndDuplicateNames() {
        var first = new MenuDTO(1, "같은 이름", 5000, 1, "식사", "Y");
        var second = new MenuDTO(2, "같은 이름", 9000, 1, "식사", "Y");
        var unavailable = new MenuDTO(3, "품절", 1000, 1, "식사", "N");
        var result = RecommendationService.selectMenus(List.of(first, second, unavailable),
                mapper.readTree("{\"maxPrice\":6000,\"menuCodes\":[1,1,2,3,999]}"));
        assertEquals(List.of(1), result.stream().map(MenuDTO::getMenuCode).toList());
    }

    @Test void rejectsUnauthenticatedCsrfMismatchAndLoggedOutSession() {
        assertEquals(401, assertThrows(CartException.class, () -> AiSession.require(null, "x")).getStatus());
        var session = session();
        assertEquals(403, assertThrows(CartException.class, () -> AiSession.require(session, "wrong")).getStatus());
        assertDoesNotThrow(() -> AiSession.require(session, "csrf"));
        session.invalidate();
        assertEquals(401, assertThrows(CartException.class, () -> AiSession.require(session, "csrf")).getStatus());
    }

    @Test void websocketRequiresCsrfAndStopsAfterLogout() throws Exception {
        var http = session();
        var socket = mock(WebSocketSession.class);
        when(socket.getId()).thenReturn("ai-test");
        when(socket.isOpen()).thenReturn(true);
        when(socket.getAttributes()).thenReturn(new HashMap<>(Map.of("httpSession", http)));
        socketHandler.afterConnectionEstablished(socket);
        socketHandler.handleMessage(socket, new TextMessage("{\"type\":\"auth\",\"csrfToken\":\"csrf\"}"));
        verify(socket).sendMessage(argThat(message -> ((TextMessage) message).getPayload().contains("auth_ok")));
        http.invalidate();
        socketHandler.handleMessage(socket, new TextMessage("{\"type\":\"draft\",\"menuName\":\"test\"}"));
        verify(socket).close(argThat(status -> status.getCode() == 1008));
        verifyNoInteractions(ai);
        socketHandler.afterConnectionClosed(socket, CloseStatus.NORMAL);
    }

    @Test void rejectsOversizedAiFieldsBeforeSaving() {
        var request = new MenuDTO(0, "테스트", 1000, categories.findAll().get(0).getCategoryCode(), null, "Y");
        request.setMenuIngredients("가".repeat(1001));
        assertThrows(IllegalArgumentException.class, () -> menus.saveMenu(request));
    }

    private static MockHttpSession session() {
        var session = new MockHttpSession();
        session.setAttribute(MemberSession.MEMBER, 1L);
        session.setAttribute("AUTH_CSRF_TOKEN", "csrf");
        return session;
    }
}
