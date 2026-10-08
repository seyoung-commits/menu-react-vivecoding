package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ohgiraffers.springdatajpa.common.ResponseMessage;
import com.ohgiraffers.springdatajpa.dto.CategoryDTO;
import com.ohgiraffers.springdatajpa.service.CategoryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "카테고리 관리", description = "카테고리 목록·상세 조회 API")
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    // @Autowired를 작성하지 않아도 자동 적용됨을 잊지 말자.
    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @Operation(summary = "카테고리 전체 조회",
            description = "전체 카테고리를 조회합니다. result.categories에 카테고리 목록이 담깁니다. 각 항목은 categoryCode(번호), categoryName(이름), refCategoryCode(상위 번호), refCategoryName(상위 이름)으로 구성됩니다. 최상위 카테고리는 상위 번호와 이름이 null입니다. 메뉴 등록 화면에서는 하위 카테고리를 선택합니다. 이 API는 카테고리별 메뉴 목록을 반환하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카테고리 목록 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ResponseMessage> findAllCategories() {
        List<CategoryDTO> categories = categoryService.findAllCategories();

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("categories", categories);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "카테고리 목록 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    @Operation(summary = "카테고리 상세 조회",
            description = "카테고리 번호로 한 건을 조회합니다. result.category에 categoryCode, categoryName, refCategoryCode, refCategoryName이 담깁니다. 최상위 카테고리의 refCategoryCode와 refCategoryName은 null입니다. 존재하지 않으면 HTTP 404와 ERROR_CODE_00002를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "카테고리 상세 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "404", description = "카테고리가 존재하지 않음 (ERROR_CODE_00002)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{categoryCode}")
    public ResponseEntity<ResponseMessage> findCategoryByCode(@Parameter(description = "조회할 카테고리의 고유 번호", example = "4") @PathVariable int categoryCode) {
        CategoryDTO category = categoryService.findCategoryByCode(categoryCode);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("category", category);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "카테고리 상세 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }
    // 전체 카테고리 조회

    // 단일 카테고리 조회
}
