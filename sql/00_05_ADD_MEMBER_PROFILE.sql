-- 기존 회원과 약관을 유지하고 프로필 사진 URL 필드만 추가한다. 한 번만 적용한다.
ALTER TABLE tbl_member
    ADD COLUMN profile_image_url VARCHAR(2048) NULL AFTER nickname;
