-- 기존 메뉴 데이터는 유지한다. 적용 전 image_path 컬럼 존재 여부를 확인하고 한 번만 실행한다.
ALTER TABLE tbl_menu ADD COLUMN image_path VARCHAR(255) NULL;
