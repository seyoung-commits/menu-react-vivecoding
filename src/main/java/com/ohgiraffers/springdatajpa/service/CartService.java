package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.entity.*;
import com.ohgiraffers.springdatajpa.exception.CartException;
import com.ohgiraffers.springdatajpa.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CartService {
    private final CartItemRepository items;
    private final MemberRepository members;
    private final MenuRepository menus;
    private final MenuImageStorage images;

    public CartService(CartItemRepository items, MemberRepository members,
            MenuRepository menus, MenuImageStorage images) {
        this.items = items; this.members = members; this.menus = menus; this.images = images;
    }

    @Transactional(readOnly = true)
    public CartDTO get(Long memberCode) {
        if (memberCode == null || !members.existsById(memberCode)) throw loginRequired();
        return view(memberCode);
    }

    @Transactional
    public CartDTO add(Long memberCode, CartItemRequest request) {
        lock(memberCode);
        if (request == null || request.menuCode() == null || request.menuCode() < 1)
            throw invalid("올바른 메뉴 번호가 필요해요.");
        quantity(request.quantity());
        Menu menu = menus.findById(request.menuCode())
                .orElseThrow(() -> new CartException("CART_MENU_NOT_FOUND", 404, "메뉴가 삭제됐거나 존재하지 않아요."));
        if (!available(menu)) throw new CartException("CART_MENU_UNAVAILABLE", 409, "현재 주문할 수 없는 메뉴예요.");
        CartItem item = items.findByMemberCodeAndMenuCode(memberCode, menu.getMenuCode()).orElse(null);
        if (item == null) {
            if (items.countByMemberCode(memberCode) >= 50)
                throw invalid("장바구니에는 최대 50종의 메뉴를 담을 수 있어요.");
            items.saveAndFlush(new CartItem(memberCode, menu.getMenuCode(), request.quantity()));
        } else {
            int combined = item.getQuantity() + request.quantity();
            quantity(combined);
            item.setQuantity(combined);
            items.flush();
        }
        return view(memberCode);
    }

    @Transactional
    public CartDTO update(Long memberCode, Long itemCode, Integer quantity) {
        lock(memberCode);
        quantity(quantity);
        CartItem item = ownItem(memberCode, itemCode);
        item.setQuantity(quantity);
        items.flush();
        return view(memberCode);
    }

    @Transactional
    public CartDTO remove(Long memberCode, Long itemCode) {
        lock(memberCode);
        items.delete(ownItem(memberCode, itemCode));
        items.flush();
        return view(memberCode);
    }

    @Transactional
    public Checkout checkout(Long memberCode) {
        lock(memberCode);
        List<CartItem> rows = items.findByMemberCodeOrderByCartItemCodeAsc(memberCode);
        if (rows.isEmpty()) throw new CartException("CART_EMPTY", 409, "장바구니에 메뉴를 먼저 담아 주세요.");
        Map<Integer, Menu> currentMenus = loadMenus(rows);
        List<PaymentItem> orderItems = new ArrayList<>();
        long total = 0;
        int totalQuantity = 0;
        for (CartItem row : rows) {
            Menu menu = currentMenus.get(row.getMenuCode());
            if (menu == null || !available(menu))
                throw new CartException("CART_MENU_UNAVAILABLE", 409, "주문할 수 없는 메뉴를 장바구니에서 삭제해 주세요.");
            quantity(row.getQuantity());
            long amount = (long) menu.getMenuPrice() * row.getQuantity();
            total += amount;
            totalQuantity += row.getQuantity();
            if (total > Integer.MAX_VALUE) throw invalid("결제 가능한 총금액을 초과했어요.");
            orderItems.add(new PaymentItem(row.getCartItemCode(), row.getVersion(),
                    menu.getMenuCode(), menu.getMenuName(), menu.getMenuPrice(), row.getQuantity(), (int) amount));
        }
        String name = orderItems.get(0).menuName();
        if (orderItems.size() > 1) name += " 외 " + (orderItems.size() - 1) + "종";
        return new Checkout(memberCode, List.copyOf(orderItems), name, totalQuantity, (int) total);
    }

    @Transactional
    public void completePurchase(Long memberCode, List<PaymentItem> purchased) {
        lock(memberCode);
        for (var item : purchased) {
            if (item.cartItemCode() != null && item.cartVersion() != null)
                items.deletePurchased(memberCode, item.cartItemCode(), item.cartVersion());
        }
    }

    private CartDTO view(Long memberCode) {
        List<CartItem> rows = items.findByMemberCodeOrderByCartItemCodeAsc(memberCode);
        Map<Integer, Menu> currentMenus = loadMenus(rows);
        List<CartDTO.Item> result = new ArrayList<>();
        long total = 0;
        int totalQuantity = 0;
        boolean payable = !rows.isEmpty();
        for (CartItem row : rows) {
            Menu menu = currentMenus.get(row.getMenuCode());
            boolean available = menu != null && available(menu);
            int price = menu == null ? 0 : menu.getMenuPrice();
            long amount = (long) price * row.getQuantity();
            result.add(new CartDTO.Item(row.getCartItemCode(), row.getMenuCode(),
                    menu == null ? "삭제된 메뉴" : menu.getMenuName(), price,
                    menu == null ? null : images.publicUrl(menu.getImagePath()), row.getQuantity(), amount, available));
            total += amount; totalQuantity += row.getQuantity(); payable &= available;
        }
        return new CartDTO(List.copyOf(result), result.size(), totalQuantity, total,
                payable && total > 0 && total <= Integer.MAX_VALUE);
    }

    private Map<Integer, Menu> loadMenus(List<CartItem> rows) {
        return menus.findAllById(rows.stream().map(CartItem::getMenuCode).toList())
                .stream().collect(Collectors.toMap(Menu::getMenuCode, menu -> menu));
    }
    private CartItem ownItem(Long memberCode, Long itemCode) {
        if (itemCode == null || itemCode < 1) throw invalid("올바른 장바구니 항목 번호가 필요해요.");
        return items.findByCartItemCodeAndMemberCode(itemCode, memberCode)
                .orElseThrow(() -> new CartException("CART_ITEM_NOT_FOUND", 404, "내 장바구니에서 해당 항목을 찾지 못했어요."));
    }
    private void lock(Long memberCode) {
        if (memberCode == null || members.lockByMemberCode(memberCode).isEmpty()) throw loginRequired();
    }
    private boolean available(Menu menu) { return "Y".equals(menu.getOrderableStatus()) && menu.getMenuPrice() > 0; }
    private void quantity(Integer value) {
        if (value == null || value < 1 || value > 99) throw invalid("메뉴별 수량은 1~99개로 입력해 주세요.");
    }
    private CartException invalid(String detail) { return new CartException("CART_INVALID", 400, detail); }
    private CartException loginRequired() { return new CartException("LOGIN_REQUIRED", 401, "장바구니를 이용하려면 로그인해 주세요."); }
    public record Checkout(Long memberCode, List<PaymentItem> items,
            String name, int quantity, int totalAmount) {}
}
