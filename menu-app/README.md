# menu-app — 메뉴 관리

chap06 REST API(`http://localhost:8080`)에 붙는 React 앱. 지침은 상위 폴더의 `AGENTS.md`.

## 실행

```bash
npm install
npm run dev      # 토큰 생성 후 http://localhost:5173 (5173 고정, 사용 중이면 멈춘다)
npm run lint
npm run build
```

Spring Boot 서버가 먼저 떠 있어야 한다. 서버 CORS 는 5173 만 허용한다.

## 화면

| 주소 | 화면 |
| --- | --- |
| `/menus?q=&category=&price=&sort=&page=` | 목록 · 검색 · 카테고리 · 가격(초과) · 정렬 · 페이지 |
| `/menus/:menuCode` | 상세 · 수정 · 삭제(확인 모달) |
| `/menus/new`, `/menus/:menuCode/edit` | 등록·수정 공용 폼 |

- 조건이 없으면 서버 페이징(`/api/menus/pages`, `/pages/sort`)을 쓰고,
  이름·카테고리·가격 조건이 있으면 전체(`/api/menus`) 또는 가격 검색(`/api/menus/search`) 결과를 받아 화면에서 거르고 나눈다.
  서버에 이름 검색과 카테고리별 조회가 없기 때문이다.
- 개발 모드(StrictMode)에서는 effect 가 두 번 돌아 네트워크 탭에 같은 요청이 두 번 보인다. `npm run build` 결과물에서는 한 번이다.

## 디자인

- `design/montage.tokens.json` → `npm run tokens` → `src/tokens.css` (직접 고치지 않는다)
- 모서리는 `radius` 그룹(`--radius-3` … `--radius-20`, `--radius-full`)으로 추가했다. Montage 컴포넌트 `style.ts` 에서 뽑은 값이다.
- `design/mockup.html` — 아트보드 5장(레이아웃·목록·상세·폼·상태). 앱과 같은 `src/index.css` 를 불러 그린다.
  `npm run dev` 중에 `http://localhost:5173/design/mockup.html` 로 연다.
