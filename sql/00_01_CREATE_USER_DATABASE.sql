-- CHANGE_ME_DB_PASSWORD를 본인이 정한 DB 비밀번호로 바꾸고, 같은 값을 DB_PASSWORD 환경변수에 등록하세요.
-- ------------------------------------------------------------------
-- 계정 생성 후 데이터베이스 활용
-- ------------------------------------------------------------------

-- 1) 현재 접속한 데이터베이스(=스키마) 확인
-- mysql이 출력될 것이며, 만약 지정된 DB가 없다면 NULL이 출력됨.
SELECT DATABASE();

-- 만약, 위에서 mysql이 나오지 않는다면, 아래 SQL을 실행해 작업할 데이터베이스를 mysql로 선택.
USE mysql;

-- 시스템 데이터베이스란?
-- 데이터베이스를 설치하고 최초 초기화할 때 사용자가 의도적으로 설치하지 않아도 자동으로 구성되는 데이터베이스다.
-- MySQL에는 총 4개의 시스템 데이터베이스가 존재하는데, 우리는 mysql에 접속중이다.
-- mysql은 MySQL 서버가 정상적으로 실행되고 관리되는데에 있어서 필수적인 핵심 정보를 저장하는 공간이다.
-- 사용자 계정 및 권한, 타임존, 로그, 서버 운영 메타데이터 등을 포함하고 있다.

-- 2) root 대신 사용할 실습용 ohgiraffers 계정 새로 만들기
-- 'localhost'를 사용해도 되지만, '%'(와일드카드)를 쓰면 모든 호스트에서의 접속을 허용하여 외부에서도 접속 가능하다.
CREATE USER 'ohgiraffers'@'%' IDENTIFIED BY  'CHANGE_ME_DB_PASSWORD';

-- MySQL를 설치할때 자동 구성된 root는 MySQL 서버의 모든 권한을 가진 슈퍼유저이므로 실무에서도 거의 사용하지 않는다.
-- 이를 연습하고자 최소 권한 원칙(Principle of Least Privilege)에 따라 실습용 계정을 만들어 사용할 예정이다.

-- ohgiraffers 계정이 잘 생성되었는지 확인
SELECT user, host FROM user;

-- 3) 데이터베이스 생성 후 계정에 권한 부여
-- 실습에 사용할 menudb 데이터베이스 생성
CREATE DATABASE menudb;

-- 데이터베이스 목록을 확인해 menudb가 존재하는지 확인
SHOW DATABASES;

-- 왼쪽 Navigator를 새로고침해서 menudb database(schema)가 추가된 것을 확인한다.
-- MySQL은 개념적으로 database와 schema를 구분하지 않는다.
-- (CREATE DATABASE와 CREATE SCHEMA가 같은 개념이다.)

-- 4) 생성한 menudb 데이터베이스에 대하여 ohgiraffers 유저에게 모든 권한을 부여
GRANT ALL PRIVILEGES ON menudb.* TO 'ohgiraffers'@'%';

-- ohgiraffers 유저에게 부여된 권한 확인 (2개 행이 조회되면 됨)
SHOW GRANTS FOR 'ohgiraffers'@'%';

-- 5) 새로운 접속기 생성 후 접속하고 데이터베이스 활용하기
-- 좌측 상단의 home 버튼을 눌러 ohgiraffers 계정 접속기를 만들어 접속하고 database(schema)를 사용한다.
-- 접속기의 Connection Name은 'OHGIRAFFERS'로 지정
-- Parameters의 Username은 'ohgiraffers'로 지정(계정명)
-- Default Schema(기본 데이터베이스(스키마) 설정)는 'menudb'로 지정
USE menudb;