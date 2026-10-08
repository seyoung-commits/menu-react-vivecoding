package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.controller.CartController;
import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.entity.*;
import com.ohgiraffers.springdatajpa.exception.*;
import com.ohgiraffers.springdatajpa.repository.*;
import com.ohgiraffers.springdatajpa.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

// 실제 MySQL의 UNIQUE·FK·버전·행 잠금을 사용하며 테스트 데이터는 전부 롤백한다.
@SpringBootTest @Transactional
class CartIntegrationTests {
    @Autowired CartService carts;
    @Autowired CartItemRepository items;
    @Autowired MemberRepository members;
    @Autowired MenuRepository menus;
    @Autowired CategoryRepository categories;
    Long owner, other;
    Menu first, second;

    @BeforeEach void fixtures() {
        members.upsert(900000001L, 1L, "장바구니 테스트", null, false);
        members.upsert(900000001L, 2L, "다른 회원 테스트", null, false);
        owner = members.findByKakaoAppIdAndKakaoUserId(900000001L, 1L).orElseThrow().getMemberCode();
        other = members.findByKakaoAppIdAndKakaoUserId(900000001L, 2L).orElseThrow().getMemberCode();
        Category category = categories.findAll().get(0);
        first = menus.saveAndFlush(new Menu(0, "테스트 메뉴 하나", 4500, "Y", category));
        second = menus.saveAndFlush(new Menu(0, "테스트 메뉴 둘", 1200, "Y", category));
    }
    CartDTO add(Menu menu, int quantity) { return carts.add(owner, new CartItemRequest(menu.getMenuCode(), quantity)); }

    @Test void sameMenuMergesQuantityAndIsIsolatedByMember() {
        add(first, 2);
        var cart = add(first, 3);
        assertEquals(1, cart.itemCount());
        assertEquals(5, cart.totalQuantity());
        assertEquals(22500, cart.totalAmount());
        assertEquals(1, items.countByMemberCode(owner));
        assertEquals(0, carts.get(other).itemCount());
        assertEquals(5, carts.get(owner).items().get(0).quantity());
    }

    @Test void quantityLimitsAndMissingOrUnavailableMenusDoNotOverwriteExistingCart() {
        var cart = add(first, 99);
        assertThrows(CartException.class, () -> add(first, 1));
        assertEquals(99, carts.get(owner).totalQuantity());
        for (Integer invalid : new Integer[] {null, 0, -1, 100})
            assertThrows(CartException.class, () -> carts.update(owner, cart.items().get(0).cartItemCode(), invalid));
        second.setOrderableStatus("N"); menus.flush();
        assertEquals("CART_MENU_UNAVAILABLE", assertThrows(CartException.class, () -> add(second, 1)).getCode());
        assertEquals("CART_MENU_NOT_FOUND", assertThrows(CartException.class,
                () -> carts.add(owner, new CartItemRequest(Integer.MAX_VALUE, 1))).getCode());
    }

    @Test void otherMemberCannotChangeOrDeleteMyItemAndOwnerCan() {
        Long id = add(first, 2).items().get(0).cartItemCode();
        assertEquals("CART_ITEM_NOT_FOUND", assertThrows(CartException.class,
                () -> carts.update(other, id, 7)).getCode());
        assertThrows(CartException.class, () -> carts.remove(other, id));
        assertEquals(3, carts.update(owner, id, 3).totalQuantity());
        assertEquals(0, carts.remove(owner, id).itemCount());
        assertEquals(0, carts.get(owner).totalQuantity());
    }

    @Test void checkoutUsesCurrentDatabasePricesAndRejectsNewlyUnavailableMenus() {
        add(first, 2); add(second, 1);
        first.setMenuPrice(5000); menus.flush();
        var checkout = carts.checkout(owner);
        assertEquals(11200, checkout.totalAmount());
        assertEquals(3, checkout.quantity());
        assertEquals(2, checkout.items().size());
        assertEquals(5000, checkout.items().get(0).unitPrice());
        second.setOrderableStatus("N"); menus.flush();
        assertFalse(carts.get(owner).checkoutAllowed());
        assertEquals("CART_MENU_UNAVAILABLE", assertThrows(CartException.class, () -> carts.checkout(owner)).getCode());
        assertEquals(2, carts.get(owner).itemCount());
    }

    @Test void approvalRemovesOnlyUnchangedPurchasedRowsAndRetryPreservesNewRows() {
        Long firstId = add(first, 2).items().get(0).cartItemCode();
        add(second, 1);
        var snapshot = carts.checkout(owner);
        carts.update(owner, firstId, 3);
        carts.completePurchase(owner, snapshot.items());
        assertEquals(1, carts.get(owner).itemCount());
        assertEquals(3, carts.get(owner).totalQuantity());
        add(second, 2); // 이미 삭제된 메뉴를 새 항목 번호로 다시 담는다.
        carts.completePurchase(owner, snapshot.items());
        assertEquals(2, carts.get(owner).itemCount());
        assertEquals(5, carts.get(owner).totalQuantity());
    }

    @Test void emptyCartAndOverflowCannotPreparePayment() {
        assertEquals("CART_EMPTY", assertThrows(CartException.class, () -> carts.checkout(owner)).getCode());
        add(first, 2); first.setMenuPrice(Integer.MAX_VALUE); menus.flush();
        assertFalse(carts.get(owner).checkoutAllowed());
        assertEquals("CART_INVALID", assertThrows(CartException.class, () -> carts.checkout(owner)).getCode());
    }

    @Test void deletingMenuRemovesItsCartItemThroughDatabaseForeignKey() {
        add(first, 1); menus.delete(first); menus.flush();
        assertEquals(0, carts.get(owner).itemCount());
    }

    @Test void endpointsRequireLoginAndCsrfAndIgnoreClientMemberNumber() throws Exception {
        var mvc = MockMvcBuilders.standaloneSetup(new CartController(carts))
                .setControllerAdvice(new ExceptionController()).build();
        mvc.perform(get("/api/cart")).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("LOGIN_REQUIRED"));
        var session = new MockHttpSession();
        session.setAttribute(MemberSession.MEMBER, owner);
        session.setAttribute("AUTH_CSRF_TOKEN", "test-csrf");
        String body = "{\"menuCode\":"+first.getMenuCode()+",\"quantity\":2,\"memberCode\":"+other+"}";
        mvc.perform(post("/api/cart/items").session(session).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("AUTH_CSRF_INVALID"));
        mvc.perform(post("/api/cart/items").session(session).header("X-CSRF-Token", "test-csrf")
                .contentType(MediaType.APPLICATION_JSON).content(body)).andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.result.cart.totalQuantity").value(2));
        assertEquals(2, carts.get(owner).totalQuantity());
        assertEquals(0, carts.get(other).itemCount());
        Long itemCode = carts.get(owner).items().get(0).cartItemCode();
        mvc.perform(delete("/api/cart/items/"+itemCode).session(session).header("X-CSRF-Token", "wrong"))
                .andExpect(status().isForbidden());
        assertEquals(1, carts.get(owner).itemCount());
    }
}
