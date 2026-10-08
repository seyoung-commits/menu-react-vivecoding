# menu-react-vivecoding

React와 Spring Boot로 만든 메뉴 관리 서비스입니다. 메뉴 등록·수정·검색, 사진 업로드,
카카오 로그인, 장바구니와 카카오페이 테스트 결제, AI 메뉴 추천·설명·사진 생성을 제공합니다.

## 주요 기능

- 카테고리·가격·이름 조건으로 메뉴 조회 및 페이지 이동
- 메뉴 재료·설명과 사진을 함께 등록·수정
- OpenAI를 이용한 메뉴 추천: SSE로 답변과 실제 메뉴 카드를 전달
- WebSocket으로 설명 초안·수정, 사진 생성·진행 상태·취소 처리
- 카카오 로그인 세션과 CSRF 검증을 사용하는 AI 기능
- 기본 더미 메뉴 36개와 재료·설명 데이터

## 개발 환경

- Java 17, Spring Boot 4.1.1, Gradle wrapper
- MySQL
- Node.js 20.19 이상 또는 22.12 이상, npm
- React, Vite, React Router, Axios

서버는 `http://localhost:8080`, 프론트는 `http://localhost:5173`을 사용합니다.
5173 포트로 실행해야 서버의 허용된 origin과 일치합니다.

## 환경변수 등록

비밀 값은 Windows 환경변수 또는 IntelliJ의 실행 설정에 등록하세요.
`.env.example`은 변수 목록을 보여주는 예시이며 Spring Boot가 자동으로 읽지 않습니다.
Windows 환경변수를 변경한 뒤에는 IntelliJ와 터미널을 다시 열어야 합니다.

| 변수 | 용도 |
| --- | --- |
| `DB_PASSWORD` | 필수. 사용 중인 MySQL 계정의 비밀번호 |
| `DB_USERNAME` | 선택. 기본값 `ohgiraffers` |
| `DB_URL` | 선택. 기본값 `jdbc:mysql://localhost:3306/menudb` |
| `KAKAO_REST_API_KEY` | 카카오 로그인 REST API 키 |
| `KAKAO_CLIENT_SECRET` | 카카오 앱의 Client Secret을 사용하면 등록 |
| `OPENAI_API_KEY` | AI 메뉴 추천·설명·사진 생성 |
| `KAKAOPAY_SECRET_KEY` | 카카오페이 테스트 결제 |
| `KAKAO_SYNC_ENABLED` | 간편가입/약관 설정을 완료한 경우에만 `true` |
| `KAKAO_SYNC_REQUIRED_TERMS` | 카카오 앱에 설정한 필수 약관 태그 |
| `OPENAI_TEXT_MODEL`, `OPENAI_IMAGE_MODEL` | 모델을 변경할 때 선택 설정 |
| `MENU_UPLOAD_DIR` | 사진 저장 폴더. 기본값 `./uploads/menu-images` |

카카오 개발자센터에 Redirect URI `http://localhost:8080/api/auth/kakao/callback`을 등록하세요.
AI 기능은 카카오 로그인 후 사용할 수 있습니다. 결제는 테스트 결제 설정입니다.

## DB 준비

처음 설치하는 **빈 DB**에 다음 SQL을 순서대로 적용하세요.

1. `sql/00_01_CREATE_USER_DATABASE.sql`: 예시 비밀번호 `CHANGE_ME_DB_PASSWORD`를 본인이 정한 값으로 바꾸세요. 이 값이 `DB_PASSWORD`와 같아야 합니다. 이미 계정/DB가 있다면 생략합니다.
2. `sql/00_02_DB_SCRIPT.sql`: 기본 테이블과 더미 메뉴를 만듭니다. **테이블을 삭제하고 다시 만들기 때문에 기존 데이터가 있는 DB에서는 실행하지 마세요.**
3. `sql/00_03_ADD_MENU_IMAGE.sql`부터 `sql/00_07_ADD_MENU_AI.sql`까지 번호순으로 적용합니다. 이미 해당 테이블/컬럼이 있으면 적용 여부를 확인하고 생략합니다.
4. `sql/00_08_FILL_MENU_AI_DATA.sql`: 기본 메뉴의 비어 있는 재료·설명을 채웁니다. 기존에 작성한 내용은 유지합니다.

기존 프로젝트 DB에서는 초기화 SQL을 다시 실행하지 말고, 아직 적용하지 않은 변경 SQL만 실행하세요.
실제 회원·주문·결제 데이터, DB 백업, 업로드 사진은 이 저장소에 포함하지 않습니다.

## 실행

프로젝트 루트에서 서버를 실행합니다.

```powershell
.\gradlew.bat bootRun
```

다른 터미널에서 프론트를 실행합니다.

```powershell
cd menu-app
npm ci
npm run dev
```

`http://localhost:5173/menus`에 접속하세요.
API 문서는 서버 실행 후 `http://localhost:8080/swagger-ui.html`에서 확인합니다.
macOS/Linux에서는 `chmod +x gradlew` 후 `./gradlew bootRun`을 사용합니다.

## 확인

```powershell
.\gradlew.bat test
cd menu-app
npm run lint
npm run build
```

서버 테스트에도 `DB_PASSWORD`와 테스트 가능한 로컬 DB가 필요합니다.
일반 테스트는 실제 OpenAI를 호출하지 않습니다.
실제 API 검증은 `AI_PRACTICE.md`의 `OPENAI_RUN_LIVE_TESTS` 설정을 참고하세요.

## 폴더

- `src/`: Spring 서버 및 테스트
- `menu-app/`: React 프론트
- `sql/`: DB 초기화, 변경, 더미 메뉴 재료·설명 SQL
- `AI_PRACTICE.md`: AI 기능과 통신/검증 안내
- `KAKAO_LOGIN_PRACTICE.md`, `KAKAOPAY_PRACTICE.md`: 인증·결제 안내

`.idea`, `.env`, 로컬 설정, 업로드 파일, 빌드 결과, 의존성 폴더는 Git에서 제외합니다.
API 키·DB 비밀번호는 코드에 넣지 마세요.
