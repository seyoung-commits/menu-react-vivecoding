package com.ohgiraffers.springdatajpa.exception;

import com.ohgiraffers.springdatajpa.common.ErrorResponse;
import org.springframework.http.HttpStatus;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;

/* 설명. @ControllerAdvice
 *  모든 컨트롤러에서 발생한 예외를 한곳에서 가로채 처리하는 전역 예외 처리기다.
 *  ----------------------------------------------------------------------------------
 *  이것이 없으면 서비스에서 던진 예외가 그대로 올라가 500(Internal Server Error)이 응답된다.
 *  "없는 메뉴를 조회했다"는 것은 서버가 고장난 상황이 아니라 요청이 잘못된 상황이므로,
 *  상황에 맞는 상태 코드(404, 400 등)로 바꿔서 응답해주어야 한다.
 *  ----------------------------------------------------------------------------------
 *  각 컨트롤러마다 try-catch를 반복해서 작성할 필요가 없어지는 것도 큰 장점이다.
 * */
@ControllerAdvice
public class ExceptionController {

    /* 설명. 조회하려는 자원이 없는 경우 -> 404 Not Found
     *  요청 자체는 올바른데 그 자원이 존재하지 않는 상황이다.
     * */
    @ExceptionHandler(MenuNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleMenuNotFoundException(MenuNotFoundException e) {

        ErrorResponse errorResponse = new ErrorResponse(
                "ERROR_CODE_00001",
                "메뉴 조회 실패",
                e.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    @ExceptionHandler(CategoryNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleCategoryNotFoundException(CategoryNotFoundException e) {

        ErrorResponse errorResponse = new ErrorResponse(
                "ERROR_CODE_00002",
                "카테고리 조회 실패",
                e.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.NOT_FOUND);
    }

    /* 설명. 클라이언트가 잘못된 값을 보낸 경우 -> 400 Bad Request
     *  예를 들어 메뉴를 등록하면서 존재하지 않는 카테고리 코드를 함께 보낸 경우가 여기 해당한다.
     *  이때는 '메뉴'라는 자원이 없는 것이 아니라 요청에 담긴 값이 잘못된 것이므로 404가 아닌 400이 맞다.
     * */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException e) {

        ErrorResponse errorResponse = new ErrorResponse(
                "ERROR_CODE_00003",
                "잘못된 요청 값",
                e.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(InvalidMenuImageException.class)
    public ResponseEntity<ErrorResponse> handleImage(InvalidMenuImageException e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("IMAGE_INVALID", "사진을 확인해 주세요", e.getMessage()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<ErrorResponse> handleImageSize(MaxUploadSizeExceededException e) {
        return ResponseEntity.status(413).body(
                new ErrorResponse("IMAGE_TOO_LARGE", "사진 용량이 너무 커요", "사진은 5MB 이하로 골라 주세요."));
    }

    @ExceptionHandler({MissingServletRequestPartException.class, HttpMessageNotReadableException.class})
    public ResponseEntity<ErrorResponse> handleRequestBody(Exception e) {
        return ResponseEntity.badRequest().body(
                new ErrorResponse("ERROR_CODE_00003", "입력 내용을 확인해 주세요", "메뉴 정보를 올바른 JSON 형식으로 보내 주세요."));
    }

    /* 설명. 위에서 처리하지 못한 나머지 예외 -> 500 Internal Server Error
     *  ----------------------------------------------------------------------------------
     *  주의. 실제 운영 환경에서는 e.getMessage()를 그대로 내려주면 안 된다.
     *       내부 구조나 SQL 정보가 그대로 노출될 수 있기 때문이다.
     *       여기서는 학습 목적으로 원인을 바로 확인할 수 있도록 그대로 담았다.
     * */
    @ExceptionHandler(KakaoPayException.class)
    public ResponseEntity<ErrorResponse> handleKakaoPay(KakaoPayException e) {
        return ResponseEntity.status(e.getStatus()).body(
                new ErrorResponse(e.getCode(), "카카오페이 테스트 결제 준비 실패", e.getMessage()));
    }
    @ExceptionHandler(CartException.class)
    public ResponseEntity<ErrorResponse> handleCart(CartException e) {
        return ResponseEntity.status(e.getStatus()).cacheControl(org.springframework.http.CacheControl.noStore())
                .body(new ErrorResponse(e.getCode(), "장바구니 요청을 처리하지 못했어요", e.getMessage()));
    }
    @ExceptionHandler(AiUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleAiUnavailable(AiUnavailableException error) {
        return ResponseEntity.status(503).body(new ErrorResponse("AI_UNAVAILABLE", "AI 기능을 사용할 수 없어요", error.getMessage()));
    }
    @ExceptionHandler({AiRequestException.class, org.springframework.core.task.TaskRejectedException.class})
    public ResponseEntity<ErrorResponse> handleAiRequest(RuntimeException error) {
        return ResponseEntity.status(502).body(new ErrorResponse("AI_REQUEST_FAILED", "AI 요청에 실패했어요", error.getMessage()));
    }
    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(org.springframework.web.bind.MethodArgumentNotValidException error) {
        return ResponseEntity.badRequest().body(new ErrorResponse("ERROR_CODE_00003", "입력 내용을 확인해 주세요",
                error.getBindingResult().getAllErrors().get(0).getDefaultMessage()));
    }
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception e) {

        ErrorResponse errorResponse = new ErrorResponse(
                "ERROR_CODE_99999",
                "서버 내부 오류",
                e.getMessage()
        );

        return new ResponseEntity<>(errorResponse, HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
