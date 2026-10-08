# AI 메뉴 추천·설명·사진 생성

phase-3-2의 OpenAI 호출 코드와 WebSocket 설명/사진 기능을 chap06에 이식했다.
서버 Java 17, 기존 카카오 로그인 세션, 메뉴 사진 업로드를 사용한다.

## 실행

서버 명령은 `chap06-spring-data-jpa` 폴더에서, 프론트 명령은 같은 단계의 `menu-app` 폴더에서 실행한다.

1. 이번 작업 환경의 menudb에는 두 컬럼을 이미 추가했다. 다른 기존 DB에는 chap06-spring-data-jpa/sql/00_07_ADD_MENU_AI.sql을 한 번 적용한다. 이미 두 컬럼이 있으면 재실행하지 않는다.
2. DB_PASSWORD를 실제 MySQL 계정 비밀번호로 등록한다. IntelliJ의 기존 Chap06SpringDataJpaApplication 실행 설정으로 서버를 실행한다. 기존 KAKAO_REST_API_KEY, KAKAO_CLIENT_SECRET, KAKAO_SYNC_ENABLED, KAKAOPAY_SECRET_KEY 설정을 유지하고 OPENAI_API_KEY도 전달되어야 한다. 터미널에서 ./gradlew.bat bootRun을 실행하면 IntelliJ에만 저장된 환경변수는 자동으로 전달되지 않으므로 함께 설정해야 한다.
3. menu-app에서 npm run dev. http://localhost:5173에 접속한다.
4. 카카오 로그인 후 오른쪽 아래 AI 메뉴 추천을 사용한다.
5. 메뉴 등록/수정에서 이름, 카테고리, 가격, 재료를 입력한다.
6. AI 설명 만들기 → 고쳐 쓰기 → 설명에 적용. AI 사진 만들기 → 이 사진 선택.
7. 등록/수정 버튼을 눌러 저장한다. 설명·재료는 DB, 사진은 기존 업로드 디렉터리에 저장된다.

## 통신

- POST /api/ai/recommendations: 세션 쿠키 + X-CSRF-Token. SSE 이벤트 filter, candidates, delta, recommended, done, error.
- /ws/ai/menu-draft: 세션 쿠키로 연결. 첫 메시지 {"type":"auth","csrfToken":"/api/auth/me의 값"}.
- 이후 draft, revise, image, cancel 메시지를 사용한다. 연결마다 설명 대화를 따로 기억한다.
- 추천은 주문 가능한 실제 메뉴 번호를 선택한다. 이름 중복, 가격 제한, 존재하지 않는 번호는 서버에서 검증한다.
- 대상 DB에 알레르기 표가 없으므로 등록된 재료만 사용하며 알레르기 안전성을 추측하지 않는다.
- API 키는 서버에서만 사용한다. 미설정 503, 호출 실패 502, 로그인 없음 401, CSRF 불일치 403.
- 기본 모델은 원본과 같은 gpt-6-luna / gpt-image-2.5-flare. OPENAI_TEXT_MODEL, OPENAI_IMAGE_MODEL로 변경할 수 있다.

## 확인

./gradlew.bat test
menu-app: npm run lint && npm run build

실제 API 검증은 OPENAI_RUN_LIVE_TESTS=true일 때 ./gradlew.bat test --tests '*AiLiveTests' --rerun-tasks로 실행한다.
실제 호출이 발생하므로 평소 전체 테스트에서는 이 검증을 건너뛴다.

이번 이식에서 확인한 것: 기존 전체 회귀 테스트, AI 인증·필드 저장 테스트, 실제 HTTP SSE 및 WebSocket 테스트,
실제 OpenAI 설명 생성·수정·사진 생성, multipart 저장 및 DB 재조회, React lint/build, 브라우저 화면.
실제 API 검증에서 저장한 메뉴·사진은 롤백했다. 카카오 로그인 버튼은 기존 인증 절차를 그대로 사용한다.

공식 이미지 스트리밍 규격: https://developers.openai.com/api/docs/guides/image-generation
