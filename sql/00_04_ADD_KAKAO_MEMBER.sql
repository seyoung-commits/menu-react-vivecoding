-- 추가 전용 마이그레이션. 기존 메뉴/카테고리/사진 데이터는 유지한다.
CREATE TABLE IF NOT EXISTS tbl_member (
    member_code BIGINT NOT NULL AUTO_INCREMENT,
    kakao_app_id BIGINT NOT NULL,
    kakao_user_id BIGINT NOT NULL,
    nickname VARCHAR(100) NOT NULL,
    sync_completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at DATETIME NOT NULL,
    last_login_at DATETIME NOT NULL,
    PRIMARY KEY (member_code),
    CONSTRAINT uk_member_kakao UNIQUE (kakao_app_id, kakao_user_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS tbl_member_term (
    member_term_code BIGINT NOT NULL AUTO_INCREMENT,
    member_code BIGINT NOT NULL,
    term_tag VARCHAR(100) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    required_consent BOOLEAN NOT NULL,
    agreed BOOLEAN NOT NULL,
    agreed_at VARCHAR(40),
    PRIMARY KEY (member_term_code),
    CONSTRAINT uk_member_term UNIQUE (member_code, term_tag),
    CONSTRAINT fk_term_member FOREIGN KEY (member_code) REFERENCES tbl_member(member_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
