package com.ohgiraffers.springdatajpa.exception;

/* 설명. 요청한 메뉴가 존재하지 않을 때 발생시키는 예외.
 *  ----------------------------------------------------------------------------------
 *  RuntimeException(unchecked)을 상속한 이유는 두 가지다.
 *  1) checked 예외로 만들면 서비스와 컨트롤러에 throws 선언이 줄줄이 붙어 코드가 지저분해진다.
 *  2) @Transactional은 기본적으로 unchecked 예외에서만 롤백한다.
 *     checked 예외로 만들면 예외가 났는데도 트랜잭션이 커밋되어버릴 수 있다.
 *  (04_spring_boot의 chap09에서는 checked 예외로 만들어봤으니 차이를 비교해보자)
 * */
public class MenuNotFoundException extends RuntimeException {

    public MenuNotFoundException(String message) {
        super(message);
    }
}
