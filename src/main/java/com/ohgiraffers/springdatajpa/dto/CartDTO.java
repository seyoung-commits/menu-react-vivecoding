package com.ohgiraffers.springdatajpa.dto;

import java.util.List;

public record CartDTO(List<Item> items, int itemCount, int totalQuantity,
        long totalAmount, boolean checkoutAllowed) {
    public record Item(Long cartItemCode, int menuCode, String menuName, int menuPrice,
            String imageUrl, int quantity, long lineTotal, boolean available) {}
}
