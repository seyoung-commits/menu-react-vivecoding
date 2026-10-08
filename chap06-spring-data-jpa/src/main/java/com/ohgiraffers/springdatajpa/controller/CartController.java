package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.common.ResponseMessage;
import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.exception.CartException;
import com.ohgiraffers.springdatajpa.service.*;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestController @RequestMapping("/api/cart")
@Tag(name = "회원 장바구니", description = "로그인 세션에서 회원을 식별. 변경 요청은 X-CSRF-Token 필요.")
public class CartController {
    private final CartService carts;
    public CartController(CartService carts) { this.carts = carts; }

    @GetMapping
    @Operation(summary = "내 장바구니 조회", description = "result.cart: items, itemCount(메뉴 종류 수), totalQuantity, totalAmount, checkoutAllowed. items에는 cartItemCode, menuCode, menuName, menuPrice, imageUrl, quantity, lineTotal, available.")
    public ResponseEntity<ResponseMessage> get(HttpServletRequest request) {
        return response(carts.get(member(request.getSession(false))));
    }

    @PostMapping("/items")
    @Operation(summary = "메뉴 담기", description = "menuCode, quantity(1~99). 이미 담은 메뉴는 수량 합산. 회원번호·가격은 받지 않음. result.cart 반환.")
    public ResponseEntity<ResponseMessage> add(HttpServletRequest request,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf, @RequestBody CartItemRequest body) {
        HttpSession session = request.getSession(false);
        Long member = member(session); MemberSession.checkCsrf(session, csrf);
        return response(carts.add(member, body));
    }

    @PutMapping("/items/{itemCode}")
    @Operation(summary = "내 항목 수량 변경", description = "quantity(1~99)를 최종 수량으로 설정. result.cart 반환.")
    public ResponseEntity<ResponseMessage> update(HttpServletRequest request,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf,
            @PathVariable Long itemCode, @RequestBody CartItemRequest body) {
        HttpSession session = request.getSession(false);
        Long member = member(session); MemberSession.checkCsrf(session, csrf);
        return response(carts.update(member, itemCode, body.quantity()));
    }

    @DeleteMapping("/items/{itemCode}")
    @Operation(summary = "내 항목 삭제", description = "다른 회원 항목은 변경 불가. result.cart 반환.")
    public ResponseEntity<ResponseMessage> remove(HttpServletRequest request,
            @RequestHeader(name = "X-CSRF-Token", required = false) String csrf, @PathVariable Long itemCode) {
        HttpSession session = request.getSession(false);
        Long member = member(session); MemberSession.checkCsrf(session, csrf);
        return response(carts.remove(member, itemCode));
    }

    static Long member(HttpSession session) {
        if (session != null) {
            try {
                if (session.getAttribute(MemberSession.MEMBER) instanceof Long member && member > 0) return member;
            } catch (IllegalStateException ignored) {}
        }
        throw new CartException("LOGIN_REQUIRED", 401, "장바구니를 이용하려면 로그인해 주세요.");
    }
    private ResponseEntity<ResponseMessage> response(CartDTO cart) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(new ResponseMessage(200, "장바구니 조회·변경 성공", Map.of("cart", cart)));
    }
}
