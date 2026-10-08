-- 기존 테이블과 데이터를 초기화하지 않고 장바구니만 추가한다.
CREATE TABLE IF NOT EXISTS tbl_cart_item (
    cart_item_code BIGINT NOT NULL AUTO_INCREMENT,
    member_code BIGINT NOT NULL,
    menu_code INT NOT NULL,
    quantity INT NOT NULL,
    item_version BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY (cart_item_code),
    CONSTRAINT uk_cart_member_menu UNIQUE (member_code, menu_code),
    CONSTRAINT chk_cart_quantity CHECK (quantity BETWEEN 1 AND 99),
    CONSTRAINT fk_cart_member FOREIGN KEY (member_code) REFERENCES tbl_member(member_code) ON DELETE CASCADE,
    CONSTRAINT fk_cart_menu FOREIGN KEY (menu_code) REFERENCES tbl_menu(menu_code) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
