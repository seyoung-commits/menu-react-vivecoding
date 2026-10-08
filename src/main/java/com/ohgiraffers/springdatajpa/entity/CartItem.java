package com.ohgiraffers.springdatajpa.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tbl_cart_item", uniqueConstraints = @UniqueConstraint(
        name = "uk_cart_member_menu", columnNames = {"member_code", "menu_code"}))
public class CartItem {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cart_item_code")
    private Long cartItemCode;
    @Column(name = "member_code", nullable = false)
    private Long memberCode;
    @Column(name = "menu_code", nullable = false)
    private int menuCode;
    @Column(nullable = false)
    private int quantity;
    @Version @Column(name = "item_version", nullable = false)
    private long version;

    protected CartItem() {}
    public CartItem(Long memberCode, int menuCode, int quantity) {
        this.memberCode = memberCode;
        this.menuCode = menuCode;
        this.quantity = quantity;
    }
    public Long getCartItemCode() { return cartItemCode; }
    public Long getMemberCode() { return memberCode; }
    public int getMenuCode() { return menuCode; }
    public int getQuantity() { return quantity; }
    public long getVersion() { return version; }
    public void setQuantity(int quantity) { this.quantity = quantity; }
}
