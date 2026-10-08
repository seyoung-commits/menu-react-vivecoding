# 장바구니 구현 학습 가이드

## 직접 사용해 보기

1. 카카오로 로그인한다.
2. 메뉴 상세에서 수량을 선택하고 **장바구니 담기**를 누른다.
3. 같은 메뉴를 다시 담으면 새 줄 대신 기존 수량이 늘어난다.
4. 헤더의 장바구니를 열어 수량을 바꾸거나 항목을 삭제한다.
5. **장바구니 결제하기**를 누르면 담긴 메뉴 전체로 카카오페이 테스트 결제를 준비한다.
6. 승인되면 구매한 항목이 장바구니에서 사라진다. 취소·실패 시에는 남는다.

헤더 숫자는 전체 수량이 아니라 **서로 다른 메뉴 종류 수**다. 메뉴 2종을 각각 3개와 2개 담았다면 헤더는 2, 장바구니 전체 수량은 5다.

## 1. 저장: 왜 DB를 쓰는가?

장바구니는 `menudb.tbl_cart_item`에 저장한다. 로그인한 회원 번호와 메뉴 번호를 연결하는 테이블이다.

| 컬럼 | 의미 |
| --- | --- |
| `cart_item_code` | 장바구니 한 줄의 고유 번호 |
| `member_code` | 이 장바구니의 주인. `tbl_member` 참조 |
| `menu_code` | 담긴 메뉴. `tbl_menu` 참조 |
| `quantity` | 담은 수량, 1~99 |
| `item_version` | 수량 변경 등을 구분하는 버전 |

`member_code + menu_code`는 중복될 수 없다. 같은 회원이 같은 메뉴를 두 번 담으면 기존 행의 수량을 더한다. 회원당 최대 50종까지 담을 수 있다. 로그아웃해도 DB 행은 남으므로 같은 계정으로 다시 로그인하면 복원된다. 메뉴를 삭제하면 외래 키의 `ON DELETE CASCADE`에 따라 그 메뉴의 장바구니 행도 삭제된다.

새 테이블은 `sql/00_06_ADD_CART.sql`로 추가했다. 기존 메뉴·회원·결제 테이블을 초기화하지 않았다. 다른 PC에 옮길 때에도 이 추가 SQL만 적용하면 된다.

## 2. 요청: 브라우저에서 서버까지

```text
메뉴 상세 / CartPage
       ↓ api/cart.js 또는 api/payments.js
CartController / KakaoPayController
       ↓ 로그인 세션 확인 + 변경 요청의 CSRF 토큰 확인
CartService
       ↓ 수량·메뉴 상태·소유자 검사
CartItemRepository → tbl_cart_item
```

프론트가 회원 번호나 결제 금액을 보내도록 만들지 않았다. 서버가 로그인 세션에서 회원 번호를 가져오고 DB에서 가격을 계산한다. 다른 사람의 장바구니 항목 번호를 보내도 조회·수정·삭제할 수 없다. 변경 요청은 로그인 조회에서 받은 CSRF 토큰을 `X-CSRF-Token` 헤더에 넣어야 한다.

| API | 보내는 값 | 역할 |
| --- | --- | --- |
| `GET /api/cart` | 없음 | 내 장바구니와 합계 조회 |
| `POST /api/cart/items` | `{"menuCode": 1, "quantity": 2}` | 메뉴 담기. 같은 메뉴는 수량 합산 |
| `PUT /api/cart/items/{itemCode}` | `{"quantity": 3}` | 해당 항목의 수량을 3으로 변경 |
| `DELETE /api/cart/items/{itemCode}` | 없음 | 내 장바구니 항목 삭제 |
| `POST /api/payments/kakaopay/cart/ready?returnToApp=true` | 본문 없음 | 전체 장바구니로 테스트 결제 준비 |

장바구니 조회·변경 응답은 `result.cart` 안에 `items`, `itemCount`, `totalQuantity`, `totalAmount`, `checkoutAllowed`를 담는다. `api/cart.js`가 응답 템플릿에서 이 값을 꺼내 화면에 전달한다. 변경이 성공하면 `cart-changed` 이벤트를 보내 헤더와 장바구니 화면을 다시 조회한다.

## 3. 계산: 화면에 보이는 금액을 믿어도 되는가?

화면의 합계는 사용자를 위한 표시다. 결제 준비 시 서버가 현재 메뉴 가격과 주문 가능 여부를 다시 읽는다. 예를 들어 3,000원 메뉴 2개와 4,200원 메뉴 1개면 수량 3개, 총금액 10,200원이 된다. 품절·삭제·잘못된 수량·금액 범위 초과는 결제 준비 전에 차단한다.

`CartService.checkout()`이 이 계산을 하고 `PaymentItem` 목록에 당시 이름·가격·수량을 보관한다. `KakaoPayService.readyCart()`는 이 주문 정보를 기존 결제 준비 흐름으로 전달한다. 카카오페이에는 대표 메뉴명과 나머지 종류 수, 전체 수량, 총금액을 보낸다. 결과 화면에는 개별 메뉴 목록도 표시한다. 결제 준비 요청 형식은 [카카오페이 단건 결제 공식 문서](https://developers.kakaopay.com/docs/payment/online/single-payment)를 참고했다.

## 4. 정리: 결제 버튼을 누르면 바로 비우는가?

비우지 않는다. 준비 → 카카오페이 화면 → 승인 확인 순서가 끝나야 정리한다. 승인 응답의 주문 번호·회원 번호·수량·금액이 서버에 저장한 주문과 일치하는지도 검사한다.

결제 준비 시 각 장바구니 행의 번호와 버전을 저장한다. 승인 후에도 번호·버전이 같은 행만 삭제한다. 결제 도중 다른 탭에서 수량을 변경하거나 항목을 삭제하고 다시 담았다면 그 행은 유지한다. 변경한 수량에서 구매 수량을 빼는 방식이 아니라, 수정한 항목 전체를 보존하는 방식이다.

승인은 성공했지만 DB 정리에 실패하면 결과 화면에 정리가 지연됐다는 안내와 **상태 다시 조회** 버튼이 나온다. 상태 조회는 정리만 재시도하며 결제를 다시 승인하지 않는다. 새로고침에도 같은 항목을 중복 처리하지 않는다.

## 5. 코드 읽는 순서

1. `menu-app/src/pages/CartPage.jsx`: 목록·입력·합계·버튼이 화면에 어떻게 나오는지 읽는다.
2. `menu-app/src/api/cart.js`: 클릭이 어떤 HTTP 요청이 되는지 읽는다.
3. `src/main/java/com/ohgiraffers/springdatajpa/controller/CartController.java`: 로그인 회원과 CSRF 검사 위치를 찾는다.
4. `src/main/java/com/ohgiraffers/springdatajpa/service/CartService.java`: `add`, `update`, `remove`, `checkout`, `completePurchase`를 순서대로 읽는다.
5. `src/main/java/com/ohgiraffers/springdatajpa/entity/CartItem.java`와 `repository/CartItemRepository.java`: DB 저장과 조건부 삭제를 확인한다.
6. `service/KakaoPayService.java`: `readyCart`와 `finishCart`가 기존 결제 흐름에 연결되는 부분을 읽는다.

`@Transactional`은 여러 DB 작업을 하나의 작업 단위로 묶는다. 중간에 실패하면 변경을 되돌린다. 회원 행을 잠그는 이유는 여러 탭에서 동시에 같은 메뉴를 담아도 수량이나 중복 행이 꼬이지 않게 하기 위해서다. `@Version`은 장바구니 행이 결제 준비 이후 바뀌었는지 구별하는 데 사용한다.

## 확인한 내용과 실습 범위

- 백엔드 전체 79개 테스트 통과. 장바구니 DB·HTTP 테스트 8개와 결제 연결 테스트 5개를 추가했다.
- 회원별 분리, 중복 담기, 수량 범위, 현재 가격, 주문 불가 메뉴, 결제 성공·취소·실패·불확실 상태, 정리 재시도를 검증했다.
- DB를 사용하는 테스트 데이터는 트랜잭션으로 되돌린다.
- 프론트엔드 `npm run lint`, `npm run build` 통과.
- 결제는 기존 `TC0ONETIME` 테스트 CID를 사용한다. 실제 과금용 결제 설정은 추가하지 않았다.
- 장바구니는 DB에 저장하지만 결제 이력은 기존과 같이 로그인 세션별 최근 1건이다. 서버 재시작 후 주문 이력 조회를 지원하는 주문 DB까지 추가한 것은 아니다.

프론트 실행은 프로젝트 루트가 아니라 `menu-app` 폴더에서 `npm.cmd run dev`를 실행한다. 서버는 8080, 프론트는 5173을 사용한다.
