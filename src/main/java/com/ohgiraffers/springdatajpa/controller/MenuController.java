package com.ohgiraffers.springdatajpa.controller;

import com.ohgiraffers.springdatajpa.common.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import com.ohgiraffers.springdatajpa.common.ResponseMessage;
import com.ohgiraffers.springdatajpa.dto.MenuDTO;
import com.ohgiraffers.springdatajpa.service.MenuService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.multipart.MultipartFile;
import io.swagger.v3.oas.annotations.media.Encoding;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Tag(name = "메뉴 관리", description = "메뉴 조회·등록·수정·삭제 API")
@RestController
@RequestMapping("/api/menus")
public class MenuController {

    private final MenuService menuService;

    // @Autowired를 작성하지 않아도 자동 적용됨을 잊지 말자.
    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /* 목차. 1. 모든 메뉴 조회 */

    @Operation(summary = "메뉴 전체 조회",
            description = "전체 메뉴를 메뉴 번호 내림차순으로 조회합니다. result.menus에 MenuDTO 목록이 담깁니다. 각 메뉴에는 menuCode, menuName, menuPrice, categoryCode, categoryName, orderableStatus, imageUrl이 포함됩니다. imageUrl은 서버 기준 사진 주소이며 사진이 없으면 null입니다. 이름 검색과 카테고리 필터는 이 API에서 제공하지 않습니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 목록 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping
    public ResponseEntity<ResponseMessage> findAllMenus() {

        List<MenuDTO> menus = menuService.findAllMenus();

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("menus", menus);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "메뉴 목록 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 2. 페이징 처리된 메뉴 목록 조회 */
    /**
     * 주어진 Pageable 정보를 바탕으로 메뉴 리스트를 조회하고, 페이지네이션 정보를 포함한 응답을 반환한다.
     *
     * <p>{@link org.springframework.data.domain.Pageable} 객체를 인자로 받아 페이지 요청 정보를
     * 처리한다. @Parameter(hidden = true) @PageableDefault 어노테이션을 통해 기본 페이지 설정을 지정할 수 있다.</p>
     *
     * @param pageable {@link org.springframework.data.domain.Pageable} 객체로, 페이지 번호, 크기, 정렬 정보를 관리한다.
     * @return 페이징 처리된 메뉴 목록과 페이지 정보를 포함한 ResponseEntity 객체
     */
    @Operation(summary = "메뉴 목록 페이지 조회",
            description = "메뉴 번호 내림차순으로 페이지를 조회합니다. 요청의 page와 응답의 result.number는 1부터 시작합니다. result.content에 메뉴 목록, result.totalElements에 전체 개수, result.totalPages에 전체 페이지 수, result.size에 페이지 크기, result.number에 현재 페이지 번호, result.first와 result.last에 첫 페이지와 마지막 페이지 여부가 담깁니다.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (1부터 시작)",
                    schema = @Schema(type = "integer", defaultValue = "1"), example = "1"),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지당 메뉴 개수",
                    schema = @Schema(type = "integer", defaultValue = "10"), example = "10")
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "페이징 처리된 메뉴 목록 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pages")

    public ResponseEntity<ResponseMessage> findMenuPage(@Parameter(hidden = true) @PageableDefault Pageable pageable) {

        System.out.println("pageable = " + pageable);

        Page<MenuDTO> menuPage = menuService.findMenuList(pageable);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("content", menuPage.getContent());              // 현재 페이지의 데이터
        resultMap.put("totalElements", menuPage.getTotalElements());  // 전체 데이터 수
        resultMap.put("totalPages", menuPage.getTotalPages());        // 전체 페이지 수
        resultMap.put("size", menuPage.getSize());                    // 페이지 크기
        /* 설명. one-indexed-parameters 설정은 '요청'의 page 파라미터에만 적용된다.
         *  Page.getNumber()는 여전히 0부터 시작하므로, 요청과 응답의 기준을 맞추기 위해 +1 해서 내려준다.
         * */
        resultMap.put("number", menuPage.getNumber() + 1);            // 현재 페이지 번호(1부터 시작)
        resultMap.put("first", menuPage.isFirst());                   // 첫 페이지 여부
        resultMap.put("last", menuPage.isLast());                     // 마지막 페이지 여부

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "페이징 처리된 메뉴 목록 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 3. 정렬 기능이 추가된 페이징 처리 메뉴 목록 조회 */
    /**
     * 사용자가 지정한 페이지, 크기, 정렬 기준, 정렬 방향에 따라 메뉴 목록을 조회한다.
     * 
     * <p>클라이언트에서 전달한 파라미터로 페이징 및 정렬을 적용하여 메뉴 목록을 조회하고,
     * 결과를 반환한다. 정렬 기준과 방향을 동적으로 지정할 수 있다.</p>
     *
     * <p>페이지 번호와 크기는 위 findMenuPage()와 동일하게 Pageable로 받는다.
     * 그래야 one-indexed-parameters 설정이 두 엔드포인트에 똑같이 적용되어
     * 같은 page 값이 항상 같은 페이지를 가리키게 된다.
     * (page와 size를 int로 직접 받으면 이 설정이 적용되지 않아 두 API의 기준이 어긋난다)</p>
     *
     * @param pageable 페이지 번호와 크기를 담은 Pageable 객체 (기본 크기: 5)
     * @param sortBy 정렬 기준 필드 (기본값: menuPrice)
     * @param direction 정렬 방향 (asc 또는 desc, 기본값: asc)
     * @return 페이징 및 정렬이 적용된 메뉴 목록과 페이지 정보를 포함한 ResponseEntity 객체
     */
    @Operation(summary = "메뉴 목록 정렬 및 페이지 조회",
            description = "sortBy와 direction으로 정렬한 메뉴 페이지를 조회합니다. 요청의 page와 응답의 result.number는 1부터 시작합니다. result.content에 메뉴 목록, result.totalElements에 전체 개수, result.totalPages에 전체 페이지 수, result.size에 페이지 크기, result.number에 현재 페이지 번호, result.first와 result.last에 첫 페이지와 마지막 페이지 여부가 담깁니다. result.sort와 result.direction에 요청한 정렬 필드와 방향도 담깁니다. sortBy에는 엔티티 필드명(예: menuPrice, menuCode, menuName, category.categoryCode)을 사용합니다. direction은 desc(대소문자 무관)이면 내림차순, 그 외에는 오름차순입니다.")
    @Parameters({
            @Parameter(name = "page", in = ParameterIn.QUERY, description = "페이지 번호 (1부터 시작)",
                    schema = @Schema(type = "integer", defaultValue = "1"), example = "1"),
            @Parameter(name = "size", in = ParameterIn.QUERY, description = "페이지당 메뉴 개수",
                    schema = @Schema(type = "integer", defaultValue = "5"), example = "5")
    })
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "페이징 처리 및 정렬이 적용된 메뉴 목록 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pages/sort")
    public ResponseEntity<ResponseMessage> findMenuPageWithSort(
            @Parameter(hidden = true) @PageableDefault(size = 5) Pageable pageable,
            @Parameter(description = "정렬할 엔티티 필드명", example = "menuPrice") @RequestParam(defaultValue = "menuPrice") String sortBy,
            @Parameter(description = "asc: 오름차순, desc: 내림차순", example = "asc") @RequestParam(defaultValue = "asc") String direction
    ) {
        // 정렬 방향 설정
        Sort.Direction sortDirection = "desc".equalsIgnoreCase(direction) ?
                Sort.Direction.DESC : Sort.Direction.ASC;

        // 정렬 객체 생성
        Sort sort = Sort.by(sortDirection, sortBy);

        // 페이징 처리된 메뉴 조회
        Page<MenuDTO> menuPage = menuService.findMenuListWithSort(pageable, sort);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("content", menuPage.getContent());              // 현재 페이지의 데이터
        resultMap.put("totalElements", menuPage.getTotalElements());  // 전체 데이터 수
        resultMap.put("totalPages", menuPage.getTotalPages());        // 전체 페이지 수
        resultMap.put("size", menuPage.getSize());                    // 페이지 크기
        /* 설명. one-indexed-parameters 설정은 '요청'의 page 파라미터에만 적용된다.
         *  Page.getNumber()는 여전히 0부터 시작하므로, 요청과 응답의 기준을 맞추기 위해 +1 해서 내려준다.
         * */
        resultMap.put("number", menuPage.getNumber() + 1);            // 현재 페이지 번호(1부터 시작)
        resultMap.put("first", menuPage.isFirst());                   // 첫 페이지 여부
        resultMap.put("last", menuPage.isLast());                     // 마지막 페이지 여부
        resultMap.put("sort", sortBy);                                // 정렬 기준 필드
        resultMap.put("direction", direction);                        // 정렬 방향

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "페이징 처리 및 정렬이 적용된 메뉴 목록 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 4. 메뉴 코드로 단일 메뉴 조회 */
    /**
     * 메뉴 코드에 해당하는 단일 메뉴를 조회한다.
     * 
     * <p>경로 변수로 전달된 메뉴 코드를 사용하여 메뉴를 조회하고, 결과를 반환한다.</p>
     *
     * @param menuCode 조회할 메뉴의 코드 (PK)
     * @return 조회된 메뉴 정보를 포함한 ResponseEntity 객체
     */

    @Operation(summary = "메뉴 상세 조회",
            description = "메뉴 번호로 한 건을 조회합니다. result.menu에 MenuDTO가 담깁니다. 메뉴가 없으면 HTTP 404와 ErrorResponse(code: ERROR_CODE_00001)를 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 상세 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "404", description = "메뉴가 존재하지 않음 (ERROR_CODE_00001)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/{menuCode}")
    public ResponseEntity<ResponseMessage> findMenuByCode(

            @Parameter(description = "대상 메뉴의 고유 번호", example = "1") @PathVariable int menuCode
    ) {

        MenuDTO menu = menuService.findMenuByCode(menuCode);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("menu", menu);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "메뉴 상세 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 5. 가격 기준 메뉴 검색 */
    /**
     * 지정된 가격을 초과하는 메뉴 목록을 조회한다.
     * 
     * <p>쿼리 파라미터로 전달된 가격보다 높은 가격의 메뉴 목록을 조회하고, 결과를 반환한다.</p>
     *
     * @param menuPrice 기준 가격
     * @return 기준 가격을 초과하는 메뉴 목록을 포함한 ResponseEntity 객체
     */
    @Operation(summary = "가격 기준 메뉴 검색",
            description = "menuPrice를 초과하는 메뉴를 조회합니다. 지정한 가격과 같은 메뉴는 포함하지 않습니다. result.menus에 메뉴 목록, result.searchPrice에 기준 가격이 담깁니다. 조건에 맞는 메뉴가 없으면 빈 목록을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "가격 기준 메뉴 조회 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/search")

    public ResponseEntity<ResponseMessage> findMenusByPrice(@Parameter(description = "이 가격을 초과하는 메뉴만 조회합니다.", example = "10000") @RequestParam Integer menuPrice) {

        List<MenuDTO> menus = menuService.findMenusByPrice(menuPrice);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("menus", menus);
        resultMap.put("searchPrice", menuPrice);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                menuPrice + "원 초과 메뉴 목록 조회 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 6. 새 메뉴 등록 */
    /**
     * 새로운 메뉴를 등록한다.
     * 
     * <p>요청 본문으로 전달된 메뉴 정보를 사용하여 새 메뉴를 등록하고, 등록된 메뉴 정보를 반환한다.</p>
     *
     * @param menuDTO 등록할 메뉴 정보
     * @return 등록된 메뉴 정보를 포함한 ResponseEntity 객체
     */
    @Operation(summary = "메뉴 등록",
            description = "새 메뉴를 등록합니다. menuCode는 서버가 생성하므로 요청에서 제외합니다. 요청 본문에 menuName(이름), menuPrice(가격), categoryCode(카테고리 번호), orderableStatus('Y': 주문 가능, 'N': 주문 불가)를 보냅니다. categoryName은 응답용 필드입니다. 성공 시 HTTP 201이며 result.menu에 imageUrl을 포함한 등록된 메뉴가 담깁니다. 사진이 있으면 multipart/form-data로 menu(JSON)와 image(JPG/PNG, 최대 5MB)를 함께 보낼 수 있습니다. 존재하지 않는 카테고리 번호는 HTTP 400과 ERROR_CODE_00003을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "메뉴 등록 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 메뉴 정보 (ERROR_CODE_00003) 또는 사진 (IMAGE_INVALID)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)

    public ResponseEntity<ResponseMessage> saveMenu(@RequestBody MenuDTO menuDTO) {

        MenuDTO savedMenu = menuService.saveMenu(menuDTO);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("menu", savedMenu);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.CREATED.value(),
                "메뉴 등록 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(responseMessage);
    }

    /* 목차. 7. 메뉴 수정 */
    /**
     * 지정된 메뉴 코드의 메뉴 정보를 수정한다.
     * 
     * <p>경로 변수로 전달된 메뉴 코드와 요청 본문으로 전달된 메뉴 정보를 사용하여 
     * 기존 메뉴를 수정하고, 수정된 메뉴 정보를 반환한다.</p>
     *
     * @param menuCode 수정할 메뉴의 코드 (PK)
     * @param menuDTO 수정할 메뉴 정보
     * @return 수정된 메뉴 정보를 포함한 ResponseEntity 객체
     */
    @Operation(summary = "메뉴 수정",
            description = "주소의 menuCode에 해당하는 메뉴를 수정합니다. 요청 본문에 menuName(이름), menuPrice(가격), categoryCode(카테고리 번호), orderableStatus('Y': 주문 가능, 'N': 주문 불가)를 보냅니다. categoryName은 응답용 필드입니다. 이름·가격·주문 가능 상태를 함께 보내세요. categoryCode가 양수이면 카테고리를 변경하고, 0 이하이면 기존 카테고리를 유지합니다. result.menu에 수정된 메뉴가 담깁니다. 사진 교체는 multipart/form-data의 image로 전송하며, 사진을 보내지 않으면 기존 사진을 유지합니다. 메뉴가 없으면 HTTP 404, 변경할 카테고리가 없으면 HTTP 400입니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 메뉴 정보 (ERROR_CODE_00003) 또는 사진 (IMAGE_INVALID)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "메뉴가 존재하지 않음 (ERROR_CODE_00001)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{menuCode}", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ResponseMessage> updateMenu(
            @Parameter(description = "대상 메뉴의 고유 번호", example = "1") @PathVariable int menuCode,
            @RequestBody MenuDTO menuDTO
    ) {

        MenuDTO updatedMenu = menuService.updateMenu(menuCode, menuDTO);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("menu", updatedMenu);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.OK.value(),
                "메뉴 수정 성공",
                resultMap
        );

        return ResponseEntity
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    /* 목차. 8. 메뉴 삭제 */
    /**
     * 지정된 메뉴 코드의 메뉴를 삭제한다.
     * 
     * <p>경로 변수로 전달된 메뉴 코드에 해당하는 메뉴를 삭제하고, 삭제 결과를 반환한다.</p>
     *
     * @param menuCode 삭제할 메뉴의 코드 (PK)
     * @return 삭제된 메뉴 코드를 포함한 ResponseEntity 객체
     */
    @Operation(summary = "메뉴 삭제",
            description = "주소의 menuCode에 해당하는 메뉴를 삭제합니다. 실제 HTTP 상태는 200이며 응답 본문의 httpStatus 값만 204입니다. result.deletedMenuCode에 삭제한 메뉴 번호가 담깁니다. 메뉴가 없으면 HTTP 404와 ERROR_CODE_00001을 반환합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 삭제 성공 (본문 httpStatus는 204)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "404", description = "메뉴가 존재하지 않음 (ERROR_CODE_00001)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "처리되지 않은 서버 오류 (ERROR_CODE_99999). 오류 본문은 code, description, detail로 구성됩니다.",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @DeleteMapping("/{menuCode}")
    public ResponseEntity<ResponseMessage> deleteMenu(@Parameter(description = "대상 메뉴의 고유 번호", example = "1") @PathVariable int menuCode) {

        menuService.deleteMenu(menuCode);

        Map<String, Object> resultMap = new HashMap<>();
        resultMap.put("deletedMenuCode", menuCode);

        ResponseMessage responseMessage = new ResponseMessage(
                HttpStatus.NO_CONTENT.value(),
                "메뉴 삭제 성공",
                resultMap
        );

        return ResponseEntity
                // 실제 NO_CONTENT(204)는 응답 바디를 포함하지 않으므로 OK(200)로 변경
                .status(HttpStatus.OK)
                .body(responseMessage);
    }

    @Operation(summary = "메뉴 등록",
            description = "메뉴 등록: JSON 요청 또는 multipart/form-data를 사용합니다. "
                    + "multipart는 menu에 메뉴 JSON, image에 선택한 JPG/PNG 사진(최대 5MB, 2천만 화소)을 담습니다. "
                    + "menuCode는 서버가 생성합니다. "
                    + "menu에는 menuName, menuPrice, categoryCode, orderableStatus(Y/N)를 보냅니다. "
                    + "result.menu에 저장된 메뉴와 imageUrl(사진이 없으면 null)이 담깁니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "multipart/form-data",
                            encoding = @Encoding(name = "menu", contentType = "application/json"))))
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "메뉴 등록 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 메뉴 정보 또는 사진 (IMAGE_INVALID)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "사진 용량 초과 (IMAGE_TOO_LARGE)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "저장 처리 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseMessage> saveMenu(
            @RequestPart("menu") MenuDTO menuDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        MenuDTO saved = menuService.saveMenu(menuDTO, image);
        return ResponseEntity.status(201)
                .body(new ResponseMessage(201, "메뉴 등록 성공", Map.of("menu", saved)));
    }

    @Operation(summary = "메뉴 수정",
            description = "메뉴 수정: JSON 요청 또는 multipart/form-data를 사용합니다. "
                    + "multipart는 menu에 메뉴 JSON, image에 선택한 JPG/PNG 사진(최대 5MB, 2천만 화소)을 담습니다. "
                    + "대상 번호는 주소의 menuCode입니다. 새 사진을 보내지 않으면 기존 사진을 유지합니다. "
                    + "menu에는 menuName, menuPrice, categoryCode, orderableStatus(Y/N)를 보냅니다. "
                    + "result.menu에 저장된 메뉴와 imageUrl(사진이 없으면 null)이 담깁니다.",
            requestBody = @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    content = @Content(mediaType = "multipart/form-data",
                            encoding = @Encoding(name = "menu", contentType = "application/json"))))
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "메뉴 수정 성공",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ResponseMessage.class))),
            @ApiResponse(responseCode = "400", description = "잘못된 메뉴 정보 또는 사진 (IMAGE_INVALID)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "404", description = "메뉴가 존재하지 않음",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "413", description = "사진 용량 초과 (IMAGE_TOO_LARGE)",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "저장 처리 실패",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PutMapping(value = "/{menuCode}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ResponseMessage> updateMenu(
            @Parameter(description = "대상 메뉴 번호") @PathVariable int menuCode,
            @RequestPart("menu") MenuDTO menuDTO,
            @RequestPart(value = "image", required = false) MultipartFile image) {
        MenuDTO saved = menuService.updateMenu(menuCode, menuDTO, image);
        return ResponseEntity.status(200)
                .body(new ResponseMessage(200, "메뉴 수정 성공", Map.of("menu", saved)));
    }
}
