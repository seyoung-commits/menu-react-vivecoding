-- 기존 메뉴 데이터는 유지한다. 두 컬럼이 아직 없을 때 한 번 적용한다.
USE menudb;
ALTER TABLE tbl_menu
    ADD COLUMN menu_ingredients VARCHAR(1000) NULL,
    ADD COLUMN menu_description VARCHAR(2000) NULL;
