# 카카오페이 연결 연습

현재 메뉴 사이트에서 테스트 결제를 시작하고, 카카오페이 인증 후 사이트의 결과 화면으로 돌아올 수 있다.
테스트 CID는 TC0ONETIME이며 Secret key(dev)를 사용한다. 실제 청구가 발생하는 운영 결제 설정은 허용하지 않는다.

## 지금 해 볼 것 하나

메뉴 상세 화면에서 수량을 선택하고 **카카오페이 테스트 결제**를 누른다.
카카오페이 테스트 화면에서 진행한 뒤 **테스트 결제가 완료됐어요** 화면으로 돌아오는지 확인한다.
Swagger는 API 자체를 확인하는 도구였고, 이제 같은 API를 사이트 버튼이 호출한다.

## 코드가 연결되는 순서

1. React의 MenuPayment가 메뉴 번호와 수량을 받는다.
2. api/payments.js가 POST /api/payments/kakaopay/ready?returnToApp=true를 호출한다.
3. Spring의 KakaoPayService가 DB 가격 × 수량으로 금액을 계산한다.
4. KakaoPayGateway가 카카오페이 ready API를 호출해 결제 화면 주소와 TID를 받는다.
5. 서버는 TID와 주문 정보를 세션에 보관하고, React는 결제 화면으로 이동한다.
6. 인증 후 카카오페이는 Spring의 success 콜백에 pg_token을 전달한다.
7. Spring은 저장한 TID·주문 번호·방문자 번호와 pg_token으로 approve API를 호출한다.
8. 응답의 주문 번호·금액 등이 저장한 정보와 일치하면 APPROVED로 기록한다.
9. 서버는 React의 /payments/result?orderId=...로 이동시킨다.
10. PaymentResultPage는 status API에서 서버가 확인한 결과를 받아 보여준다.

React가 보내는 값은 다음 두 개다.

```json
{"menuCode": 38, "quantity": 2}
```

화면의 합계는 사용자에게 보여주는 계산이다.
실제 결제 금액은 Spring이 DB 가격으로 다시 계산한다.

## 역할별 파일

| 파일 | 역할 |
| --- | --- |
| [MenuPayment.jsx](/C:/myWs/04_spring/chap06-spring-data-jpa/menu-app/src/components/MenuPayment.jsx) | 수량 입력, 준비 요청, 결제 화면 이동 |
| [payments.js](/C:/myWs/04_spring/chap06-spring-data-jpa/menu-app/src/api/payments.js) | React의 결제 관련 HTTP 요청 |
| [PaymentResultPage.jsx](/C:/myWs/04_spring/chap06-spring-data-jpa/menu-app/src/pages/PaymentResultPage.jsx) | 서버가 확인한 결과 표시 |
| [KakaoPayController.java](/C:/myWs/04_spring/chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/controller/KakaoPayController.java) | 준비 요청·콜백·상태 조회 주소 |
| [KakaoPayService.java](/C:/myWs/04_spring/chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/service/KakaoPayService.java) | 가격 계산, 세션 저장, 승인 검증, 중복 승인 방지 |
| [KakaoPayGateway.java](/C:/myWs/04_spring/chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/service/KakaoPayGateway.java) | 카카오페이 서버에 ready·approve·order 요청 |

## 왜 세션 쿠키가 필요한가

React(5173)와 Spring(8080)은 포트가 다르다.
결제 API의 axios 요청에는 withCredentials: true를 넣어 쿠키를 보낸다.
Spring의 기존 CORS 설정은 정확한 React 주소와 쿠키 사용을 허용한다.
브라우저는 같은 세션 쿠키를 success 콜백에도 보내므로 서버가 준비했던 주문을 찾을 수 있다.
다른 브라우저나 localhost 대신 127.0.0.1로 이동하면 같은 세션을 찾지 못할 수 있다.

## 키와 토큰

Secret key(dev)는 IntelliJ 환경변수 KAKAOPAY_SECRET_KEY에만 둔다.
YAML은 실제 키 대신 환경변수 참조를 사용한다.
카카오페이 API를 부르는 주체는 Spring이다. React에는 비밀 키를 전달하지 않는다.
pg_token은 카카오페이가 인증 후 보내는 토큰이다. 사용자가 직접 만들지 않는다.
서버는 pg_token을 결과 화면이나 세션에 저장하지 않는다.
React 복귀 주소에는 주문 번호만 포함한다.

## 상태를 읽는 법

- READY: 결제 화면을 준비했다. 최종 승인 전이다.
- APPROVED: 서버가 최종 승인을 확인했다.
- CANCELED: 결제 진행을 중단했다. 이미 결제된 금액을 환불하는 기능과는 다르다.
- FAILED: 결제 진행 또는 승인이 실패했다.
- UNKNOWN: 승인 결과를 확정하지 못했다. 상태 조회로 다시 확인한다.

새로고침은 저장한 결과를 보여주며 approve API를 반복하지 않는다.
응답이 유실되면 바로 재승인하지 않고 order API로 실제 상태를 확인한다.
브라우저에서 결제 화면을 뒤로 나와도 버튼을 다시 사용할 수 있다.

## 현재 학습용 제한

주문·결제 이력은 아직 DB에 저장하지 않고 브라우저 세션별 최근 1건만 보관한다.
서버 재시작이나 세션 만료 시 정보가 사라지며, 새 ready 요청은 이전 대기 건을 대체한다.
인증 전 주문은 준비 후 30분 동안 승인 가능하다.
실제 서비스로 확장하려면 주문/결제 DB 저장, 사용자 인증, 서버 재시작 후 복구 등을 추가해야 한다.

## 확인한 내용

- 서버 자동 테스트 34개 통과.
- React lint와 build 통과.
- 브라우저에서 수량별 합계, 카카오페이 화면 이동, 뒤로 돌아왔을 때 버튼 복구 확인.
- 별도 실제 테스트 세션에서 ready, 쿠키, CORS, 취소 콜백의 React 이동, CANCELED 상태 확인.
- 사용자가 Swagger에서 최종 테스트 결제 성공을 확인함.
- React의 결제 완료 화면 복귀는 사용자가 같은 브라우저에서 테스트 인증을 마쳐 최종 확인한다.

[공식 단건 결제 문서](https://developers.kakaopay.com/docs/payment/online/single-payment)
[공식 주문 조회 문서](https://developers.kakaopay.com/docs/payment/online/payment-detail)

