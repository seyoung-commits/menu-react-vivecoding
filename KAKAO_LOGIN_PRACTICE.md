# 카카오 로그인과 카카오싱크 학습 안내

프로젝트: `C:/myWs/04_spring/chap06-spring-data-jpa`

## 1. 지금 완성된 부분

카카오 로그인 → 회원 DB 저장 → 우리 사이트 세션 로그인 → 내 정보 → 로그아웃까지 구현했다. 실제 카카오 계정으로 로그인, 새로고침, 로그아웃, 재로그인을 확인했다. 같은 앱에서 같은 계정으로 다시 로그인하면 기존 회원번호를 사용한다.

카카오싱크의 약관 조회·필수 동의 검사·동의 내역 저장 코드도 구현했다. 개발자센터에서 개인 개발자 비즈 앱 전환을 완료했고, `메뉴판-TEST`(앱 ID `1597904`) 생성도 확인했다. 테스트 앱의 카카오 로그인과 간편가입은 ON이며, 학습용 필수 약관 `menu_terms_20261005`를 등록·활성화했다. REST API 키의 로그인 Redirect URI에 `http://localhost:8080/api/auth/kakao/callback`도 저장했다. 로컬 약관 주소 `http://localhost:5173/terms`는 개발자센터 등록 단계에서 허용됐다.

IntelliJ 실행 환경변수를 테스트 앱 키로 교체하고 서버를 재시작했다. 실행 중인 서버의 `syncEnabled: true`를 확인했고, 실제 테스트 앱 계정의 카카오싱크 가입이 성공했다. 회원번호 5번과 필수 약관의 동의 내역이 MySQL에 저장됐다. 이후 실제 재로그인에서도 회원번호 5번을 유지했고, 최초 동의 시각은 그대로이며 최근 로그인 시각만 갱신됐다. 카카오에서 받은 동의 시각은 `2026-10-05T08:40:35Z`이고 화면에는 한국 시간 `2026-10-05 17:40:35`로 표시한다. 학습용 로그인·간편가입·동의 저장 흐름의 실제 검증까지 완료했다.

소스의 `KAKAO_SYNC_ENABLED` 기본값은 여전히 `false`이며 현재 실행 설정의 환경변수가 `true`로 덮어쓴다. 실행 설정을 바꾸면 Spring 프로세스를 완전히 종료하고 다시 실행해야 반영된다. 닉네임을 카카오에서 받지 못하면 기본 표시인 `카카오 사용자`를 사용한다. 이 경우에도 앱 ID와 사용자 ID로 회원을 식별하므로 로그인에는 문제가 없다.

기존 메뉴 41개, 카테고리 12개를 유지했다. 결제에는 로그인 필수 조건을 추가했으며 메뉴 조회와 사진 등록의 기존 동작은 유지했다. 실제 회원번호 5번에 카카오 닉네임과 HTTPS 프로필 사진 주소가 저장된 것을 확인했고 브라우저에서도 사진이 정상적으로 로드됐다.

## 2. 전체 흐름을 먼저 이해하기

### 결제 전에 로그인이 필요한 이유와 처리

메뉴 조회는 로그인 없이 가능하지만 결제는 로그인한 회원만 시작할 수 있다. `MenuPayment`가 `/api/auth/me`로 로그인 상태를 확인하여 비로그인이면 **로그인 후 결제하기**, 로그인이면 **카카오페이 테스트 결제**를 보여준다. 확인 중이거나 확인에 실패하면 결제 요청을 보내지 않는다.

화면 버튼만 바꾸면 직접 API를 호출해 우회할 수 있다. 그래서 `KakaoPayService`에서도 세션의 `MemberSession.MEMBER` 회원번호와 DB 회원의 존재를 검사한다. 로그인하지 않았거나 회원이 삭제됐으면 `401 LOGIN_REQUIRED`를 반환하고 카카오페이를 호출하지 않는다. 요청 본문에 회원번호를 적어도 그것으로 로그인하지 않는다.

카카오페이의 `partner_user_id`는 예전 임시 방문자 UUID 대신 `member-회원번호` 형식으로 보낸다. 승인·취소·실패 콜백과 상태 조회에서도 로그인한 회원이 결제를 시작한 회원인지 검사한다. 다른 회원의 주문은 `403 PAYMENT_FORBIDDEN`으로 막는다. 로그아웃하면 로그인과 결제 세션이 종료된다. 로그인 자체가 결제 승인으로 이어지지는 않으며 메뉴 상세에서 결제를 직접 시작해야 한다.

주요 파일은 `service/KakaoPayService.java`, 공통 세션 이름을 정의한 `service/MemberSession.java`, `components/MenuPayment.jsx`, `pages/PaymentResultPage.jsx`다. 비로그인 API 우회, 삭제된 회원, 세션 만료, 로그아웃 뒤 승인, 같은 세션에서 다른 계정으로 변경한 경우를 서버 테스트로 확인했다.

### 닉네임과 프로필 사진이 화면에 나타나는 과정

테스트 앱의 동의항목에서 `profile_nickname`(닉네임)과 `profile_image`(프로필 사진)를 선택 동의로 설정했다. 카카오가 정보를 제공하려면 로그인한 사용자도 두 항목에 동의해야 한다. 기존에 가입한 회원에게도 추가 동의를 요청할 수 있도록 인가 요청에 `scope=profile_nickname,profile_image,openid`를 포함한다. 현재 테스트 앱은 OpenID Connect가 활성화되어 있어 `openid`도 포함한다. 다른 앱으로 바꿀 때는 그 앱의 동의항목과 OpenID Connect 설정을 함께 확인한다.

1. `KakaoUserResponse.Profile`이 카카오 JSON의 `nickname`, `profile_image_url`, `thumbnail_image_url`을 받는다.
2. `MemberService`가 닉네임과 사진 주소를 정리한다. 사진은 HTTPS 카카오 CDN 주소만 사용하며 큰 사진이 없으면 썸네일을 사용한다.
3. `MemberRepository.upsert()`가 `tbl_member.nickname`과 `tbl_member.profile_image_url`을 저장한다. 같은 회원이 다시 로그인하면 최신 값으로 갱신한다. 사진 파일을 직접 다운로드하거나 DB에 넣는 방식은 아니다.
4. `/api/auth/me`가 `user.nickname`과 `user.profileImageUrl`을 React에 반환한다.
5. 공통 `MemberAvatar`가 헤더, 로그인 결과, 내 정보에서 사진을 표시한다. 사진이 없거나 불러오지 못하면 닉네임 첫 글자를 표시한다.

DB의 사진 컬럼은 `sql/00_05_ADD_MEMBER_PROFILE.sql`로 추가했으며 기존 회원과 약관, 메뉴 데이터는 유지했다. `카카오 사용자`는 동의하지 않았거나 카카오가 닉네임을 제공하지 않았을 때의 기본값이다. 화면에 쓰는 이름은 카카오 프로필 닉네임이며 별도 실명 항목인 `name`은 요청하지 않는다.

코드 적용만으로 이미 저장된 기본 이름이 바뀌지는 않는다. 서버를 재시작하고 메뉴판에서 다시 로그인하여 닉네임·사진 제공에 동의하면 기존 회원의 프로필이 갱신된다. [카카오 공식 REST API 문서](https://developers.kakao.com/docs/ko/kakaologin/rest-api)

```text
React의 카카오 로그인 버튼
  → Spring /api/auth/kakao/login
  → 카카오 로그인/동의 화면
  → Spring /api/auth/kakao/callback?code=...&state=...
  → Spring이 code를 카카오 access_token으로 교환
  → Spring이 카카오에게 토큰 정보와 사용자 정보 조회
  → [싱크 모드일 때] 서비스 약관 동의 조회·검사
  → MySQL 회원 등록 또는 기존 회원 갱신
  → Spring 세션에 우리 회원번호 저장
  → React /auth/result로 이동
  → React가 /api/auth/me로 로그인 상태 확인
```

React가 “로그인 성공”이라고 표시하는 것만으로 로그인되는 것은 아니다. Spring 세션에 회원번호가 있어야 로그인 상태다. 결과 URL의 `status=SUCCESS`를 직접 입력해도 화면은 `/me`를 조회해 실제 세션을 확인한다.

### code, access_token, 세션은 서로 다르다

| 이름 | 역할 | 이 프로젝트에서 사용하는 곳 |
| --- | --- | --- |
| 인가 코드 `code` | 카카오 로그인을 마친 뒤 토큰으로 교환하는 일회용 값 | Spring 콜백에서 받는다 |
| 카카오 `access_token` | 카카오 API를 호출할 때 제시하는 값 | Spring 내부에서 사용자·약관 조회에 사용한다 |
| 우리 사이트 세션 | 이후 요청이 어떤 우리 회원의 요청인지 기억한다 | Spring에 회원번호를 저장하고 브라우저는 세션 쿠키를 보낸다 |
| REST API 키 / Client Secret | 우리 서버가 어떤 카카오 앱인지 나타내는 설정 | Spring 실행 환경변수에서 읽는다 |

카카오 토큰을 React나 localStorage에 저장하지 않는다. 회원 DB에도 토큰과 키를 저장하지 않는다. 브라우저에 반환하는 값은 회원 정보와 로그아웃 확인용 CSRF 값이다.

## 3. 파일을 읽는 순서

아래 Java 경로는 `src/main/java/com/ohgiraffers/springdatajpa/` 기준이다.

| 파일 | 이해할 내용 |
| --- | --- |
| `controller/KakaoLoginController.java` | 로그인 시작, 콜백, 현재 회원 조회, 로그아웃의 입구 |
| `service/KakaoLoginService.java` | RestClient로 외부 카카오 API를 호출하는 방법 |
| `dto/KakaoTokenResponse.java`, `KakaoTokenInfoResponse.java`, `KakaoUserResponse.java`, `KakaoTermsResponse.java` | 카카오 JSON을 Java record로 받는 방법 |
| `service/MemberService.java` | 확인된 카카오 정보를 우리 회원으로 저장하고 DTO로 반환하는 과정 |
| `entity/Member.java`, `MemberTerm.java` | DB 테이블과 Java 필드의 대응 |
| `repository/MemberRepository.java`, `MemberTermRepository.java` | 회원 조회와 중복 없는 저장 |
| `dto/MemberDTO.java` | 화면에 필요한 회원 정보만 반환하기 |
| `exception/KakaoAuthException.java` | 키나 토큰을 노출하지 않고 오류를 전달하기 |
| `menu-app/src/api/auth.js` | 로그인 주소, 내 정보 요청, 로그아웃 요청 |
| `menu-app/src/api/useAuth.js` | 로그인 상태 조회와 로그아웃 이후 화면 갱신 |
| `menu-app/src/components/AuthControls.jsx` | 헤더 로그인 버튼 / 닉네임 / 로그아웃 버튼 |
| `menu-app/src/pages/AuthResultPage.jsx` | 성공·취소·실패를 표시하고 실제 세션을 확인 |
| `menu-app/src/pages/AccountPage.jsx` | 회원 정보와 싱크 동의 내역 표시 |
| `menu-app/src/pages/TermsPage.jsx` | 학습용 이용약관 화면 |

### Controller: 요청을 받고 다음 처리로 연결한다

`login()`은 UUID로 `state`를 만들고 세션에 보관한 뒤 카카오로 보낸다. 콜백에서 돌아온 `state`가 우리가 보냈던 값인지 비교한다. 요청 후 10분이 지나거나 값이 다르면 로그인을 진행하지 않는다. 콜백 값은 한 번만 사용할 수 있도록 검사 전에 세션에서 꺼내 제거한다.

`callback()`에서는 토큰 정보의 사용자 ID와 사용자 조회 결과의 ID가 같은지 확인한다. 확인이 끝나면 `MemberService.login()`을 호출한다. 로그인 성공 시 세션 ID를 교체하고 `LOGIN_MEMBER_CODE`에 우리 회원번호를 저장한다. 기존 카카오페이 연습에 필요한 세션 속성은 로그인 시 보존한다.

`me()`는 세션의 회원번호로 DB를 조회한다. 비로그인도 오류 대신 `authenticated: false`를 반환한다. 회원 DTO에는 카카오 토큰과 비밀 키가 없다.

`logout()`은 CSRF 확인값을 검사하고 세션을 종료한다. 이 버튼은 **메뉴판 사이트 로그아웃**이다. 카카오 계정 전체를 로그아웃하거나 카카오 연결을 해제하는 기능은 아니다. 세션 종료로 진행 중인 세션 기반 테스트 결제 정보도 사라질 수 있다.

### 로그인 버튼을 눌렀는데 바로 로그인되는 이유와 변경

메뉴판 세션과 카카오 계정 세션은 별개다. 메뉴판에서 로그아웃했어도 브라우저에 카카오 로그인 상태와 기존 서비스 동의가 남아 있으면 카카오가 로그인 입력 화면을 생략하고 콜백으로 돌아올 수 있다.

사용자가 매번 카카오 로그인 화면을 확인할 수 있도록 `login()`의 인가 요청에 `.queryParam("prompt", "login")`을 추가했다. 이제 로그인 버튼을 누르면 기존 카카오 인증 여부와 관계없이 재인증 화면을 요청한다. 기존에 동의한 서비스 약관을 매번 새로 동의하게 하는 설정은 아니다. `select_account`는 계정 세션이 하나면 자동 로그인할 수 있으므로 이 요구사항에는 `login`을 사용한다. 카카오톡 인앱 브라우저에서는 재인증 로그인 옵션이 지원되지 않는다. [공식 REST API 안내](https://developers.kakao.com/docs/ko/kakaologin/rest-api)

### Service: 외부 통신과 회원 처리를 나눈다

`KakaoLoginService`는 외부 통신을 담당한다. 토큰 교환은 POST form 요청이며, 사용자·토큰 정보·약관 조회는 Bearer 토큰을 사용하는 GET 요청이다. 연결과 읽기에 시간 제한을 설정했고 카카오 오류 원문은 브라우저에 그대로 전달하지 않는다.

`MemberService`는 DB 처리를 담당한다. `@Transactional`은 회원과 약관을 하나의 저장 작업으로 묶는다. 도중에 저장이 실패하면 그 트랜잭션을 되돌린다.

### DB: 닉네임이 아니라 ID로 회원을 구분한다

`tbl_member`에는 우리 회원번호, 카카오 앱 ID, 카카오 사용자 ID, 닉네임, 싱크 완료 여부, 가입일, 최근 로그인 시각을 저장한다. `(kakao_app_id, kakao_user_id)`에 유일 제약을 걸었다.

닉네임은 바뀌거나 다른 사람과 겹칠 수 있으므로 회원 식별자로 쓰지 않는다. 같은 앱과 사용자 조합이면 기존 회원의 닉네임과 최근 로그인 시각을 갱신한다. 동시에 로그인 요청이 와도 MySQL upsert와 유일 제약으로 중복 가입을 막는다.

테스트 앱으로 바꾸면 앱 키와 앱 ID가 달라진다. 같은 카카오 계정도 별도 앱의 회원으로 구분되므로 우리 DB 회원번호가 달라질 수 있다. 앱 ID는 카카오 토큰 정보 응답에서 얻으므로 따로 환경변수에 입력하지 않는다.

`tbl_member_term`에는 회원번호, 약관 태그, 필수 여부, 동의 여부, 동의 시각을 저장한다. `(member_code, term_tag)`가 유일하다. 싱크 모드에서는 카카오가 반환한 동의 내역을 검사한 뒤 저장한다.

### React: 서버에 로그인 상태를 물어본다

내 정보와 로그아웃 요청에는 axios의 `withCredentials: true`가 있다. 브라우저가 Spring 세션 쿠키를 함께 보내도록 하는 설정이다. 화면은 `api/`의 함수를 호출하며 JSX에서 직접 axios를 호출하지 않는다.

새로고침해도 세션 쿠키가 있으면 서버가 회원을 찾아 로그인 상태를 반환한다. 현재 세션은 마지막 요청 후 30분 동안 유지된다. 서버 재시작이나 세션 만료 뒤에는 다시 로그인해야 한다.

## 4. 우리 API와 화면

| 요청 | 결과 |
| --- | --- |
| `GET /api/auth/kakao/login` | 카카오 인가 화면으로 302 이동. 주소 이동으로 호출한다 |
| `GET /api/auth/kakao/callback` | 카카오가 호출. 처리 후 React 결과 화면으로 303 이동 |
| `GET /api/auth/me` | 현재 회원·로그인 여부·CSRF 값·싱크 모드 반환 |
| `POST /api/auth/logout` | `X-CSRF-Token` 헤더를 확인한 뒤 우리 세션 종료 |

`/me`의 `result`는 다음 형태다. 숫자와 닉네임은 설명용 예시다.

```json
{
  "authenticated": true,
  "user": {
    "memberCode": 3,
    "nickname": "카카오 사용자",
    "profileImageUrl": null,
    "kakaoAppId": 12345,
    "kakaoUserId": 67890,
    "syncCompleted": false,
    "createdAt": "2026-10-05T17:00:00",
    "lastLoginAt": "2026-10-05T17:00:00",
    "terms": []
  },
  "csrfToken": "로그아웃 요청 확인값",
  "syncEnabled": false
}
```

정상 응답 전체는 기존 API와 동일하게 `{httpStatus, message, result}`로 감싼다. `/auth/result`, `/account`, `/terms`는 React 화면 주소다. Swagger는 `http://localhost:8080/swagger-ui/index.html`에서 확인한다.

## 5. 사업자번호 없이 싱크를 연습하는 순서

사업자번호 없는 개인 개발자도 본인인증과 카카오비즈니스 통합 서비스 약관 동의를 마치면 개인 개발자 비즈 앱으로 전환할 수 있다. 비즈 앱에서 만드는 테스트 앱은 간편가입 권한을 기본 제공한다. 테스트 앱은 앱 멤버만 이용할 수 있다. 사업자 채널 연결까지 포함한 운영 서비스 설정과는 구분한다. [공식 앱 설정 문서](https://developers.kakao.com/docs/ko/app-setting/app)

아래는 설정 전체 순서다. **현재 모든 설정과 실제 싱크 로그인 검증을 완료했다.** 나중에 다시 설정할 때 참고하면 된다.

1. 현재 메뉴판 앱의 **앱 → 일반 → 비즈니스 정보**에서 개인 개발자 항목을 확인한다. 본인인증과 **카카오비즈니스 통합 서비스 약관 동의**는 앱 소유자가 내용을 확인하고 직접 진행한다.
2. 개인 개발자 비즈 앱 전환을 완료한다. 실제 사업자등록번호를 입력하는 경로로 진행하지 않는다.
3. 비즈 앱의 **테스트 앱** 항목에서 학습용 테스트 앱을 만든다. 설정 복사 여부를 확인하고 테스트 앱에 카카오 로그인 ON, Redirect URI `http://localhost:8080/api/auth/kakao/callback`을 확인한다.
4. 테스트 앱의 **카카오 로그인 → 간편가입**에서 학습용 서비스 약관을 등록하고 간편가입을 활성화한다. 최소 하나의 사용 중인 서비스 약관이 필요하다. [공식 간편가입 설정 문서](https://developers.kakao.com/docs/ko/kakaologin/prerequisite)
5. 약관 예시: 한국어 제목 `메뉴판 실습 이용약관`, 영어 제목 `Menu Board Practice Terms`, 필수 동의, 태그 `menu_terms_20261005`, URL `http://localhost:5173/terms`. 태그는 서버 설정과 정확히 같아야 한다. URL은 로컬 실습 후보이며 개발자센터의 실제 등록 검증을 확인해야 한다. 외부 접근 URL이 요구되면 약관 페이지를 접근 가능한 주소에 제공한 뒤 그 주소를 등록한다.
6. 테스트 앱 REST API 키와 활성화한 Client Secret을 IntelliJ Spring 실행 설정의 Environment variables에 넣는다. 기존 카카오페이 변수는 유지한다. 비밀 값을 소스 파일이나 채팅에 붙이지 않는다.
7. 아래 환경변수를 설정하고 Spring을 다시 실행한다.

```text
KAKAO_REST_API_KEY=<테스트 앱 REST API 키>
KAKAO_CLIENT_SECRET=<테스트 앱 Client Secret>
KAKAO_SYNC_ENABLED=true
KAKAO_SYNC_REQUIRED_TERMS=menu_terms_20261005
```

8. 메뉴판에서 로그아웃한 뒤 다시 로그인한다. 카카오 동의 화면에 서비스 약관이 표시되는지 확인한다. 완료 후 `/account`에 카카오싱크 가입 방식과 약관 동의 내역이 표시되어야 한다.

`true` 설정만으로 개발자센터의 간편가입이 활성화되지는 않는다. 서버는 카카오 약관 조회 결과에 필수 태그의 실제 동의가 없으면 싱크 가입을 완료하지 않는다. 화면 체크박스나 임의의 DB 값을 카카오 동의로 간주하지 않는다.

학습용 `/terms`는 예시 약관이다. 실제 운영에 필요한 약관·개인정보 문서가 완성됐다는 의미는 아니다.

## 6. 확인한 내용과 직접 복습하는 방법

- 백엔드 전체 테스트: 66개 통과. 잘못된 state, 만료, 중복 콜백, 취소, 사용자 ID 불일치, 로그아웃 CSRF·Origin, 필수 약관 누락, 프로필 사진 JSON 파싱·HTTPS 주소 검사·재로그인 시 갱신, 비로그인 결제 차단과 주문 회원 검사 등을 검증했다.
- DB: 회원·약관 upsert를 트랜잭션에서 검증했고 기존 메뉴·카테고리 개수가 유지되는 것을 확인했다.
- 프론트엔드: `npm run lint`, `npm run build` 통과.
- 실제 브라우저: 로그인 성공, 회원 DB 저장, 새로고침 유지, 로그아웃, 같은 회원으로 재로그인 확인. 데스크톱과 375px 모바일 화면 확인.
- 싱크 검증: 카카오 응답을 모의한 테스트와 실제 가입·재로그인까지 통과. 실제 회원번호 5번의 필수 약관 태그·동의 여부·동의 시각을 읽기 전용 DB 조회로 확인했다. 내 정보 화면의 약관 제목과 한국 시간 표시도 브라우저에서 확인했다.
- 프로필 표시: 실제 닉네임과 사진이 회원번호 5번에 저장됐고 헤더에서 닉네임과 사진이 정상 표시되는 것을 확인했다. 사진 이미지의 로드 완료도 브라우저에서 확인했다.
- 결제 로그인: 로그인한 회원은 결제 버튼이 활성화되고 로그아웃 후에는 로그인 안내가 표시되는 것을 실제 화면에서 확인했다. 비로그인 준비·상태 조회 API가 실행 중인 서버에서도 401을 반환하며 결과 화면도 로그인 안내를 표시한다.

복습은 아래 순서로 진행하면 된다.

1. `KakaoLoginController.login()`에서 세션에 저장하는 값과 카카오에 보내는 값을 찾는다.
2. `callback()`에서 토큰 요청 → 사용자 요청 → DB 저장 → 세션 저장 순서를 찾는다.
3. `MemberService.login()`에서 닉네임 대신 앱 ID와 사용자 ID로 조회하는 이유를 설명해 본다.
4. 브라우저 개발자 도구 Network에서 `/api/auth/me` 응답을 확인한다. 로그아웃 전후 `authenticated`가 어떻게 달라지는지 본다.
5. 내 정보 화면의 회원번호를 기억하고 로그아웃·재로그인한다. 같은 앱에서는 같은 회원번호이고 최근 로그인 시각만 갱신되는지 확인한다.
6. 싱크 설정 후 `tbl_member_term`에서 실제 동의 태그·동의 시각을 확인한다.

추가한 SQL은 `sql/00_04_ADD_KAKAO_MEMBER.sql`이며 현재 로컬 DB에는 이미 적용했다. 기존 DB를 유지하려면 전체 초기화 SQL을 다시 실행하지 않는다.

테스트 명령은 프로젝트 폴더에서 `./gradlew.bat test`, React 폴더에서 `npm run lint`와 `npm run build`다. Java와 Node 환경이 설정되어 있어야 한다.
