# 메뉴 사진 업로드 구현 설명

서버는 `chap06-spring-data-jpa/`, 프론트는 같은 단계의 `menu-app/`에 있다. 서버 실행·테스트는 서버 폴더에서 수행한다.

프로젝트: chap06-spring-data-jpa/

## 지금 할 수 있는 것

- 메뉴 등록 시 JPG 또는 PNG 사진을 한 장 선택한다. 사진은 선택 사항이다.
- 선택한 사진을 등록 전에 미리 보고, 선택을 취소하거나 다른 사진으로 바꾼다.
- 메뉴 목록과 상세 화면에서 저장된 사진을 본다.
- 수정 시 새 사진을 선택하면 교체하고, 선택하지 않으면 기존 사진을 유지한다.
- 메뉴를 삭제하면 연결된 사진도 정리한다.
- 사진은 최대 5MB, 2천만 화소까지 받는다.

## 1. 사진 선택과 업로드는 다르다

menu-app/src/components/MenuPhotoField.jsx

파일 입력창에서 사진을 고르면 브라우저가 File 객체를 준다. File 객체에는 파일 내용, 이름, 크기, 종류가 들어 있다.
URL.createObjectURL(file)은 이 파일을 브라우저에서 미리 볼 수 있는 임시 주소를 만든다. 이 시점에는 서버로 보내지 않는다.
화면을 떠나거나 선택을 바꾸면 URL.revokeObjectURL로 임시 주소를 해제한다.
선택된 파일은 부모 폼에 전달되고, 등록 또는 수정 버튼을 눌렀을 때 전송된다.

menu-app/src/components/MenuForm.jsx

기존 이름·가격·카테고리 검증에 사진 오류 검사를 연결했다.
정상 입력이면 기존 값에 imageFile을 추가하여 onSubmit으로 넘긴다.
서버 오류는 기존과 같이 description과 detail을 폼에 표시한다.

## 2. 파일을 담는 상자는 FormData다

menu-app/src/api/menus.js

사진을 선택하지 않으면 기존처럼 JSON을 보낸다.
사진이 있으면 FormData에 두 부분을 담는다.

| 부분 이름 | 내용 | 형식 |
| --- | --- | --- |
| menu | 이름, 가격, 카테고리 번호, 주문 가능 여부 | application/json |
| image | 선택한 사진 | JPG 또는 PNG 파일 |

이 방식이 multipart/form-data다.
menu 부분은 JSON 문자열을 application/json Blob으로 감싼다. 서버가 문자열이 아니라 메뉴 객체로 해석하도록 형식을 알려주는 것이다.
boundary는 multipart의 각 부분을 구분하는 표시다. 브라우저가 자동으로 붙이므로 직접 작성하지 않는다.

menu-app/src/api/client.js

기존에 모든 요청을 application/json으로 고정하던 헤더를 제거했다.
axios가 실제 요청 본문에 맞게 JSON 또는 FormData를 처리하게 했다.

## 3. 같은 주소라도 요청 형식으로 구분한다

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/controller/MenuController.java

등록 주소는 POST /api/menus, 수정 주소는 PUT /api/menus/{menuCode}다.
각 주소에 JSON을 받는 메서드와 multipart를 받는 메서드가 있다. consumes 설정이 둘을 구분한다.

- JSON: @RequestBody MenuDTO
- multipart 메뉴 부분: @RequestPart("menu") MenuDTO
- multipart 사진 부분: @RequestPart(value = "image", required = false) MultipartFile

MultipartFile은 Spring이 업로드 파일을 다룰 때 사용하는 객체다.
required = false이므로 사진이 없어도 요청할 수 있다.
성공 응답 구조는 이전과 동일하고, result.menu에 imageUrl이 추가된다.
등록 성공은 201, 수정 성공은 200이다.

## 4. 사진과 DB는 서로 다른 곳에 저장된다

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/service/MenuImageStorage.java

서버는 원래 파일명과 확장자를 그대로 믿지 않는다.
실제 내용을 읽어 JPG/PNG인지, 파일 크기와 해상도가 제한 안인지 확인한다.
검사한 이미지를 다시 인코딩하여 UUID로 만든 이름으로 저장한다.

기본 저장 폴더:
chap06-spring-data-jpa/uploads/menu-images

기본 경로는 서버 실행 폴더 기준 ./uploads/menu-images다.
다른 실행 위치나 배포 환경에서는 MENU_UPLOAD_DIR 환경 변수로 영구 저장 위치를 지정할 수 있다.
업로드 파일은 빌드 폴더 밖에 있으며 Git에서는 제외했다.

MySQL tbl_menu의 image_path에는 서버가 만든 파일명만 저장한다.
사진 바이트 자체는 DB에 넣지 않는다.

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/entity/Menu.java
- DB의 image_path 컬럼과 연결되는 imagePath 필드를 추가했다.

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/dto/MenuDTO.java
- 화면으로 돌려줄 imageUrl 필드를 추가했다.
- 사진이 없으면 null이다.

기존 DB에는 컬럼을 추가했고, 기존 메뉴 44개는 유지했다.
새 DB를 별도로 준비한다면 초기 DB 구성 후 다음 변경 SQL을 한 번 적용한다:
chap06-spring-data-jpa/sql/00_03_ADD_MENU_IMAGE.sql

현재 DB에 다시 실행할 필요는 없다. 기존 초기화 SQL을 재실행하면 데이터가 지워지므로 사진 컬럼을 추가할 때 사용하지 않는다.

## 5. DB 저장 실패 시 사진은 어떻게 되나

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/service/MenuService.java

DB 트랜잭션은 DB 변경만 되돌린다. 사진 파일을 자동으로 삭제해 주지는 않는다.
따라서 MenuImageStorage가 트랜잭션 결과에 맞춰 파일을 정리한다.

| 상황 | 처리 |
| --- | --- |
| 사진 저장 후 메뉴 DB 저장 실패 | 새 파일 삭제 |
| 사진 교체 성공 | DB 커밋 후 이전 사진 삭제 |
| 사진 교체 실패 | 새 파일 삭제, 이전 사진 유지 |
| 메뉴 삭제 성공 | DB 커밋 후 연결된 사진 삭제 |
| 사진 없이 메뉴 수정 | 기존 사진 경로 유지 |

이 방식은 정상적인 커밋·롤백 흐름에 맞춘 처리다. 파일 삭제 자체가 운영체제 권한 등으로 실패하면 서버 로그에 남긴다.

## 6. 화면에 사진이 나타나는 과정

chap06-spring-data-jpa/src/main/java/com/ohgiraffers/springdatajpa/controller/MenuImageController.java

/api/menu-images/{filename} 요청에 이미지 파일을 반환한다.
허용된 형태의 서버 생성 파일명만 조회하므로, 사용자가 임의의 시스템 파일 경로를 지정하지 못한다.

메뉴 응답 예시:
{
  "menuCode": 7,
  "menuName": "김치찌개",
  "menuPrice": 9000,
  "imageUrl": "/api/menu-images/고유번호.jpg"
}

사진 주소가 /로 시작하므로 그대로 React의 img에 넣으면 5173 화면 서버에 요청하게 된다.
따라서 api/menus.js에서 공통 client의 baseURL을 기준으로 사진 주소를 완성한다.
이렇게 하면 서버 주소를 여러 화면에 중복해서 적지 않아도 된다.

menu-app/src/components/MenuPhoto.jsx
- 목록과 상세 화면에서 함께 사용하는 사진 컴포넌트다.
- imageUrl이 있으면 img로 보여준다.
- 사진이 없거나 읽을 수 없으면 기존 메뉴 이름 첫 글자 디자인을 보여준다.
- 사진 표시 영역의 크기는 일정하게 유지하고 object-fit으로 맞춘다.

## 7. 오류 읽는 법

| HTTP 상태 | 오류 코드 | 뜻 |
| --- | --- | --- |
| 400 | IMAGE_INVALID | 빈 파일, 이미지가 아닌 파일, 허용하지 않는 형식·해상도 등 |
| 413 | IMAGE_TOO_LARGE | 서버 업로드 용량 제한 초과 |
| 400 | ERROR_CODE_00003 | 메뉴 JSON이 없거나 잘못된 형식, 잘못된 카테고리 등 |
| 500 | ERROR_CODE_99999 | 처리되지 않은 저장 오류 |

오류 메시지는 기존 ErrorResponse의 code, description, detail 구조를 유지한다.
Swagger와 chap06-spring-data-jpa/api-docs.json에도 JSON·multipart 요청 형식, 사진 필드, 오류 응답을 반영했다.

## 확인한 내용

- Spring 컴파일 및 JUnit 9개 통과: 기존 2개 + 이미지 저장 테스트 7개.
- React lint와 프로덕션 build 통과.
- 실제 프런트 API 함수를 통한 HTTP 검증 17개 통과.
- PNG 등록, JPEG 교체, 사진 없는 등록과 수정, 목록·상세·페이징 사진 주소 확인.
- 가짜 이미지·용량 초과·필수 JSON 누락 오류 확인.
- DB 저장 실패 시 새 파일 정리, 교체·삭제 후 이전 파일 정리 확인.
- 브라우저에서 등록 폼, 목록 카드, 상세 사진, 수정 폼의 기존 사진 표시 확인.
- 파일 선택창 자동 조작은 브라우저 확장 권한 제한 때문에 완료하지 못했다. 파일 전송과 저장은 실제 프런트의 createMenu/updateMenu 함수를 이용해 확인했다.
- 검증용으로 만든 메뉴와 사진은 확인 후 정리했다.

## 직접 사용해 보기

1. http://localhost:5173/menus/new 를 연다.
2. 사진 파일을 선택하고 미리보기가 나타나는지 본다.
3. 이름·가격·카테고리를 입력하고 등록한다.
4. 상세 화면과 목록에서 사진을 확인한다.
5. 수정 화면에서 이름만 바꾸면 사진이 유지되고, 다른 사진을 선택하면 교체된다.
