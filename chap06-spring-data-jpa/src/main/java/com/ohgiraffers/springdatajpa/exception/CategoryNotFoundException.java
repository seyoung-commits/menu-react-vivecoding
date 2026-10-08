package com.ohgiraffers.springdatajpa.exception;

/* 설명. 요청한 카테고리가 존재하지 않을 때 발생시키는 예외.
 *  (MenuNotFoundException과 같은 이유로 RuntimeException을 상속한다)
 * */
public class CategoryNotFoundException extends RuntimeException {

    public CategoryNotFoundException(String message) {
        super(message);
    }
}
