# 메뉴 관리 서비스 — 에이전트 지침

디자인 토큰으로 화면을 만들고, 데이터는 chap06 REST API 서버에서 가져온다.
API 명세는 이 폴더의 `api-docs.json` 에 있고, React 앱은 `menu-app/` 에 있다.

---

## 1. 화면 디자인 규칙 (서버마다 같음)

### 1-1. 사용 라이브러리

- 앱이 쓰는 라이브러리는 react, react-dom, react-router, axios 만 쓴다.
- 프로젝트는 `npm create vite@latest <이름> -- --template react --eslint` 로 만든다.
  `--eslint` 를 빼면 다른 린터가 깔린다.
- UI 라이브러리와 CSS 프레임워크는 도입하지 않는다.
- TypeScript 문법을 쓰지 않는다.

### 1-2. 폴더 구조

```
menu-app/src/
    api/         서버 요청. 컴포넌트는 이 파일만 부른다
    components/  주소에 직접 대응하지 않는 화면 조각
    pages/       주소 하나에 대응하는 화면
```

- 주소에 직접 대응하면 pages, 아니면 components 에 둔다.

### 1-3. 상태

- 화면 하나에서만 쓰는 값은 useState 에 둔다.
- 검색어, 카테고리, 가격 조건, 페이지 번호는 state 가 아니라 URL 쿼리스트링에 둔다.
- 서버에서 받아 온 목록은 받은 화면이 갖는다.

### 1-4. 스타일 규칙

- 색은 반드시 `var(--semantic-*)` 토큰으로 쓴다. 색 값을 직접 적지 않는다.
- `font-size` 를 직접 쓰지 않는다. 타입 스타일 클래스(`.title2`, `.body1` …)와 굵기 변형(`.body1--bold`)을 쓴다.
- 간격은 `--space-*` 스케일 안에서만 고른다.
- 모서리는 `--radius-*` 안에서만 고른다. 간격 토큰을 모서리에 빌려 쓰지 않는다.
  - 배지 `--radius-6`, 작은 버튼 `--radius-8`, 버튼·입력창·카드 `--radius-12`, 떠 있는 패널 `--radius-16`, 모달 `--radius-20`, 칩·원형 `--radius-full`
- 토큰 목록은 `menu-app/design/montage.tokens.json` 을 참조한다.
- `src/tokens.css` 는 `npm run tokens` 가 만드는 생성물이다. 직접 고치지 않는다.

### 1-5. 확인

- `npm run lint` 와 `npm run build` 를 돌린다.
- 둘 다 통과해도 화면은 브라우저에서 직접 확인한다.

---

## 2. REST API 통신 규칙 (서버마다 다름)

### 2-1. 서버

- 주소는 `http://localhost:8080` 이다. `baseURL` 은 `api/` 의 axios 인스턴스 한 곳에만 적는다.
- 서버는 5173 만 허용한다. 5174 로 뜨면 요청이 막히므로 5173 으로 다시 띄운다.

### 2-2. 데이터 요청

- 서버 요청은 `api/` 폴더의 `.js` 파일에만 둔다.
- 컴포넌트(`.jsx`)에서 axios 나 fetch 를 직접 부르지 않는다.
- 요청 주소는 `menuCode` · `categoryCode` 로 조립한다. 서버가 링크를 주지 않는다.
- 한 번에 받고 끝나는 요청은 axios 를 쓴다.
- 응답을 조금씩 받아 화면에 흘려야 하는 요청은 fetch 를 쓴다. AI 메뉴 추천이 해당한다.
- 페이지 번호는 1부터 센다. 응답의 `result.number` 도 1부터다.

### 2-3. 응답 템플릿

> 정상 응답 템플릿과 오류 응답 템플릿의 형태가 다름에 주의한다.

정상 응답

```json
{ "httpStatus": 200, "message": "메뉴 목록 조회 성공", "result": { "menus": [] } }
```

오류 응답

```json
{ "code": "ERROR_CODE_00001", "description": "메뉴 조회 실패", "detail": "..." }
```

- `result` 안을 `api/` 에서 꺼내 반환한다. 컴포넌트가 템플릿을 알지 않게 한다.
- 오류는 `code` 와 `description` 으로 판단한다. 오류 응답에는 `httpStatus` 가 없다.
- 삭제 응답의 `httpStatus` 는 204 지만 실제 HTTP 상태는 200 이다.
- 등록 성공은 실제 HTTP 201 이다.
- `orderableStatus` 는 `'Y'` 또는 `'N'` 한 글자다.

| code | HTTP | 상황 |
| --- | --- | --- |
| `ERROR_CODE_00001` | 404 | 메뉴가 없다 |
| `ERROR_CODE_00002` | 404 | 카테고리가 없다 |
| `ERROR_CODE_00003` | 400 | 잘못된 요청 값 (없는 카테고리 번호 등) |
| `ERROR_CODE_99999` | 500 | 서버 내부 오류 |

### 2-4. API 명세에 없는 내용

`result` 는 `Map` 이라 명세에 내부가 비어 있다. 키 이름은 각 API 의 description 에 적혀 있다.

| API | result 키 |
| --- | --- |
| `GET /api/menus` | `menus` |
| `GET /api/menus/pages` | `content`, `totalElements`, `totalPages`, `size`, `number`, `first`, `last` |
| `GET /api/menus/search?menuPrice=` | `menus`, `searchPrice` — 지정 가격 **초과**만 온다 |
| `GET /api/menus/{menuCode}` | `menu` |
| `POST /api/menus` · `PUT /api/menus/{menuCode}` | `menu` |
| `DELETE /api/menus/{menuCode}` | `deletedMenuCode` |
| `GET /api/categories` | `categories` |

- `CategoryDTO` 스키마가 없다. 필드는 `categoryCode`, `categoryName`, `refCategoryCode`, `refCategoryName` 이다.
- 최상위 카테고리(식사·음료·디저트)는 `ref` 두 값이 null 이다. 메뉴는 하위 카테고리에 속한다.
- `ErrorResponse` 스키마는 명세에 있다. 위 오류 응답 템플릿과 같다.
- 이름 검색과 카테고리별 메뉴 조회 API 는 없다. 목록을 받아 화면에서 거른다.
- `menuName` 은 DB 에서 30자까지다. 이름이 중복될 수 있다(유일 제약 없음).
- 수정 시 `categoryCode` 가 0 이하면 서버가 기존 카테고리를 유지한다. 화면에서는 항상 양수를 보낸다.

### 2-5. 메뉴 사진
- 등록과 수정은 기존 JSON 요청과 multipart/form-data 요청을 모두 받는다.
- multipart의 menu 부분은 application/json, image 부분은 JPG/PNG 파일(최대 5MB, 2천만 화소)이다.
- 메뉴 응답에 imageUrl이 추가된다. 사진이 없으면 null이고, 있으면 서버 기준 /api/menu-images/... 주소다.
- 사진 주소는 api/에서 서버 baseURL 기준으로 변환한다. 화면에 서버 주소를 중복하지 않는다.
- 새 사진을 보내지 않는 수정은 기존 사진을 유지한다.
- IMAGE_INVALID는 400, 업로드 용량 제한 초과 IMAGE_TOO_LARGE는 413이다.
- 사진 파일은 MENU_UPLOAD_DIR(기본 ./uploads/menu-images)에 보관한다. DB에는 서버가 생성한 파일명만 저장한다.
- 기존 DB에는 sql/00_03_ADD_MENU_IMAGE.sql을 한 번 적용한다. 초기화 SQL을 재실행하지 않는다.


### 2-6. AI 기능
- AI는 기존 카카오 로그인 세션을 사용한다. JWT와 Zustand를 도입하지 않는다.
- POST /api/ai/recommendations는 fetch SSE로 읽는다. credentials: include와 X-CSRF-Token을 보낸다.
- /ws/ai/menu-draft는 WebSocket 연결 후 auth 메시지로 csrfToken을 확인한다. draft, revise, image, cancel을 사용한다.
- 스트리밍과 소켓 코드는 menu-app/src/api/ai.js에 둔다.
- 메뉴에 menuIngredients(최대 1000자), menuDescription(최대 2000자)가 추가됐다. null로 보낸 기존 수정 요청은 기존 값을 유지하고 빈 문자열은 지운다.
- AI 사진은 선택한 File로 기존 multipart 업로드에 연결한다. 생성만으로 자동 저장하지 않는다.
- API 키는 서버 환경변수 OPENAI_API_KEY에서만 읽는다. 프론트에 넣지 않는다.
- 이번 작업의 로컬 menudb에는 sql/00_07_ADD_MENU_AI.sql의 두 컬럼을 이미 적용했다.
- 일반 gradle test는 실제 OpenAI를 호출하지 않는다. 명시적 실제 API 검증은 AI_PRACTICE.md를 참고한다.
