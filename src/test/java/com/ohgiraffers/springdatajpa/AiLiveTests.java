package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.ai.client.OpenAiClient;
import com.ohgiraffers.springdatajpa.ai.dto.*;
import com.ohgiraffers.springdatajpa.ai.service.*;
import com.ohgiraffers.springdatajpa.dto.MenuDTO;
import com.ohgiraffers.springdatajpa.service.MenuService;
import com.ohgiraffers.springdatajpa.repository.CategoryRepository;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;
import java.util.*;
import javax.imageio.ImageIO;
import java.io.ByteArrayInputStream;
import static org.junit.jupiter.api.Assertions.*;

// 명시적으로 실행할 때만 실제 OpenAI 호출을 한다. 메뉴와 사진 저장은 테스트 종료 시 롤백한다.
@SpringBootTest
@Transactional
@EnabledIfEnvironmentVariable(named = "OPENAI_RUN_LIVE_TESTS", matches = "true")
class AiLiveTests {
    @Autowired MenuDraftService drafts;
    @Autowired MenuImageService images;
    @Autowired OpenAiClient client;
    @Autowired MenuService menus;
    @Autowired CategoryRepository categories;

    private MenuDraftMessage menu() {
        var menu = new MenuDraftMessage();
        menu.setMenuName("딸기 라떼"); menu.setCategoryName("음료"); menu.setMenuPrice(5500);
        menu.setMenuIngredients("딸기, 우유");
        return menu;
    }

    @Test void generatesAndRevisesDescriptionAndPersistsActualImage() throws Exception {
        var history = new ArrayList<ChatMessage>();
        var menu = menu();
        var streamed = new StringBuilder();
        String first = drafts.draft(history, menu, streamed::append);
        assertFalse(first.isBlank()); assertEquals(first, streamed.toString());
        String revised = drafts.revise(history, "한 문장으로 짧게 써줘", text -> {});
        assertFalse(revised.isBlank()); assertEquals(4, history.size());
        menu.setMenuDescription(revised);
        String image = images.generate(menu, partial -> {});
        byte[] bytes = Base64.getDecoder().decode(image);
        assertNotNull(ImageIO.read(new ByteArrayInputStream(bytes)));
        var request = new MenuDTO(0, menu.getMenuName(), 5500, categories.findAll().get(0).getCategoryCode(), null, "Y");
        request.setMenuIngredients(menu.getMenuIngredients()); request.setMenuDescription(revised);
        var saved = menus.saveMenu(request, new MockMultipartFile("image", "ai-menu.jpg", "image/jpeg", bytes));
        var loaded = menus.findMenuByCode(saved.getMenuCode());
        assertEquals(revised, loaded.getMenuDescription());
        assertNotNull(loaded.getImageUrl());
        System.out.println("AI_LIVE_OK: description, revision, JPEG generation, multipart storage, database reread");
    }

    @Test void structuredMenuSelectionOnlyReturnsActualAvailableMenu() {
        var catalog = menus.findAllMenus().stream().filter(m -> "Y".equals(m.getOrderableStatus())).limit(10).toList();
        assertFalse(catalog.isEmpty());
        var codes = catalog.stream().map(MenuDTO::getMenuCode).toList();
        var schema = Map.<String,Object>of("type", "object", "additionalProperties", false,
            "required", List.of("maxPrice", "menuCodes"), "properties", Map.of(
                "maxPrice", Map.of("type", List.of("integer", "null")),
                "menuCodes", Map.of("type", "array", "items", Map.of("type", "integer", "enum", codes))));
        JsonNode response = client.createJson(System.getenv().getOrDefault("OPENAI_TEXT_MODEL", "gpt-6-luna"), "none",
            "다음 번호 중 하나를 골라라. maxPrice는 null이다. " + codes,
            List.of(new ChatMessage("user", "메뉴 하나 추천해줘")), 300, "menu_live_test", schema);
        var selected = RecommendationService.selectMenus(catalog, response);
        assertFalse(selected.isEmpty());
        var text = new StringBuilder();
        client.streamText(System.getenv().getOrDefault("OPENAI_TEXT_MODEL", "gpt-6-luna"), "none",
            "이 메뉴를 한 문장으로 추천해줘: " + selected.get(0).getMenuName(),
            List.of(new ChatMessage("user", "추천 이유도 알려줘")), 200, text::append);
        assertFalse(text.toString().isBlank());
        System.out.println("AI_LIVE_OK: structured selection and text streaming");
    }
}
