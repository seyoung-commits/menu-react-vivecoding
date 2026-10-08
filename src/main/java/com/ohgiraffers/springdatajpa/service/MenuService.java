package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.MenuDTO;
import com.ohgiraffers.springdatajpa.entity.Category;
import com.ohgiraffers.springdatajpa.entity.Menu;
import com.ohgiraffers.springdatajpa.exception.MenuNotFoundException;
import com.ohgiraffers.springdatajpa.repository.CategoryRepository;
import com.ohgiraffers.springdatajpa.repository.MenuRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class MenuService {

	private final MenuRepository menuRepository;
	private final CategoryRepository categoryRepository;
	private final MenuImageStorage imageStorage;

	// @Autowired를 작성하지 않아도 자동 적용됨을 잊지 말자.
	public MenuService(MenuRepository menuRepository, CategoryRepository categoryRepository, MenuImageStorage imageStorage) {
		this.menuRepository = menuRepository;
		this.categoryRepository = categoryRepository;
		this.imageStorage = imageStorage;
	}

	/* 목차. 0. 엔티티와 DTO를 서로 변환하는 공통 메서드 */

	/* 설명. Menu 엔티티를 MenuDTO로 변환한다.
	 *  매핑 라이브러리를 쓰면 코드는 짧아지지만 내부적으로 리플렉션이 동작해
	 *  어떤 값이 어디로 옮겨지는지가 감춰진다.
	 *  계층 간 데이터 전달이 실제로 어떻게 일어나는지 눈으로 확인하기 위해 직접 변환한다.
	 *  ----------------------------------------------------------------------------------
	 *  엔티티는 category라는 '객체'를 들고 있지만, DTO는 categoryCode/categoryName으로
	 *  평평하게 펼쳐서 내려준다. 이 변환 지점이 바로 두 구조를 이어주는 곳이다.
	 * */
	private MenuDTO convertToDTO(Menu menu) {

		Category category = menu.getCategory();

		MenuDTO dto = new MenuDTO(
				menu.getMenuCode(),
				menu.getMenuName(),
				menu.getMenuPrice(),
				category != null ? category.getCategoryCode() : 0,
				category != null ? category.getCategoryName() : null,
				menu.getOrderableStatus()
		);
		dto.setMenuIngredients(menu.getMenuIngredients());
		dto.setMenuDescription(menu.getMenuDescription());
		dto.setImageUrl(imageStorage.publicUrl(menu.getImagePath()));
		return dto;
	}

	/* 설명. MenuDTO를 Menu 엔티티로 변환한다.
	 *  menuCode는 DB가 AUTO_INCREMENT로 채번하므로 여기서 설정하지 않는다.
	 *  카테고리는 코드값이 아니라 조회해온 Category '엔티티'를 넣어준다.
	 * */
	private Menu convertToEntity(MenuDTO menuDTO, Category category) {

		Menu menu = new Menu();

		menu.setMenuName(menuDTO.getMenuName());
		menu.setMenuPrice(menuDTO.getMenuPrice());
		menu.setOrderableStatus(menuDTO.getOrderableStatus());
		menu.setCategory(category);
		menu.setMenuIngredients(menuDTO.getMenuIngredients());
		menu.setMenuDescription(menuDTO.getMenuDescription());

		return menu;
	}

	/* 설명. 전달받은 카테고리 코드로 Category를 조회하며, 없으면 예외를 발생시킨다. */
	private Category findCategoryOrThrow(int categoryCode) {

		return categoryRepository.findById(categoryCode)
				.orElseThrow(() -> new IllegalArgumentException("유효하지 않은 카테고리 코드입니다: " + categoryCode));
	}

	/* 목차. 1. 메뉴 코드로 메뉴 조회 */
	/**
	 * 메뉴 코드로 단일 메뉴를 조회한다.
	 *
	 * <p>findById 메소드는 JpaRepository에 이미 구현되어 있으므로 별도로 정의할 필요가 없다.
	 * 반환 타입은 Optional이며, 조회 결과가 없을 경우 예외를 발생시킨다.</p>
	 *
	 * @param menuCode 조회할 메뉴의 코드
	 * @return 조회된 메뉴 정보를 담은 MenuDTO 객체
	 * @throws MenuNotFoundException 해당 메뉴 코드의 메뉴가 존재하지 않을 경우 발생
	 */
	public MenuDTO findMenuByCode(int menuCode) {
		Menu menu = menuRepository.findById(menuCode)
				.orElseThrow(() -> new MenuNotFoundException("해당 메뉴가 존재하지 않습니다. menuCode=" + menuCode));

		return convertToDTO(menu);
	}

	/* 목차. 2. 모든 메뉴 조회 */
	/**
	 * 모든 메뉴를 조회한다.
	 *
	 * <p>findAll 메소드를 이용하여 모든 메뉴를 조회하며, 메뉴 코드 기준 내림차순으로 정렬한다.
	 * 조회된 엔티티 목록은 DTO 목록으로 변환하여 반환한다.</p>
	 *
	 * @return 모든 메뉴 정보를 담은 MenuDTO 리스트
	 */
	public List<MenuDTO> findAllMenus() {
		// 가장 최근에 추가된 메뉴부터 역순으로 조회(내림차순 정렬)
		List<Menu> menuList = menuRepository.findAll(Sort.by("menuCode").descending());

		return menuList.stream()
				.map(this::convertToDTO)
				.toList();
	}

	/* 목차. 3. 페이징 처리된 메뉴 조회 */
	/**
	 * 페이징 처리된 메뉴 목록을 조회한다.
	 *
	 * <p>Pageable 객체에 담긴 페이지 정보를 이용하여 페이징 처리된 메뉴 목록을 조회한다.
	 * 넘어온 Pageable은 이미 0부터 시작하는 표준 페이지 번호를 담고 있으므로 그대로 사용한다.
	 * (요청의 page=1을 0번 인덱스로 바꾸는 일은 application.yaml의
	 * spring.data.web.pageable.one-indexed-parameters 설정이 대신 해준다)</p>
	 *
	 * @param pageable 페이지 번호, 크기, 정렬 정보를 담은 Pageable 객체
	 * @return 페이징 처리된 메뉴 정보를 담은 Page&lt;MenuDTO&gt; 객체
	 */
	public Page<MenuDTO> findMenuList(Pageable pageable) {
		// 페이지 번호와 크기는 그대로 사용하고, 이 목록에 적용할 정렬 기준만 지정한다.
		pageable = PageRequest.of(pageable.getPageNumber(),
					pageable.getPageSize(),
					Sort.by("menuCode").descending());

		Page<Menu> menuPage = menuRepository.findAll(pageable);

		/* 설명. Page가 제공하는 map()을 사용하면 페이징 정보(전체 개수, 전체 페이지 수 등)는 유지한 채
		 *  내용물만 DTO로 변환할 수 있다.
		 * */
		return menuPage.map(this::convertToDTO);
	}

	/* 목차. 4. 정렬 기능이 추가된 페이징 처리 메뉴 조회 */
	/**
	 * 사용자가 지정한 페이지, 크기, 정렬 방식에 따라 메뉴 목록을 조회한다.
	 *
	 * <p>페이지 정보(Pageable)와 정렬 정보(Sort)를 조합해 새로운 PageRequest를 만들고,
	 * 해당 객체로 페이징 처리된 메뉴 목록을 조회한다.</p>
	 *
	 * <p>바로 위 findMenuList()는 정렬 기준이 메뉴 코드 내림차순으로 고정되어 있지만,
	 * 이 메서드는 호출하는 쪽에서 정렬 기준을 전달받는다는 점이 다르다.</p>
	 *
	 * <p>정렬 기준에는 테이블의 컬럼명이 아니라 엔티티의 필드명을 사용한다.
	 * 연관 관계를 타고 들어가는 것도 가능하며, 카테고리 코드로 정렬하려면
	 * categoryCode가 아니라 category.categoryCode로 지정하면 된다.</p>
	 *
	 * @param pageable 페이지 번호와 크기를 담은 Pageable 객체
	 * @param sort 정렬 정보
	 * @return 페이징 및 정렬이 적용된 메뉴 정보를 담은 Page&lt;MenuDTO&gt; 객체
	 */
	public Page<MenuDTO> findMenuListWithSort(Pageable pageable, Sort sort) {
		// 페이지 번호와 크기는 넘어온 값을 그대로 쓰고, 정렬 기준만 교체한다.
		Pageable sortedPageable = PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);

		Page<Menu> menuPage = menuRepository.findAll(sortedPageable);

		return menuPage.map(this::convertToDTO);
	}

	/* 목차. 5. 가격 기준 메뉴 검색 */
	/**
	 * 지정된 가격을 초과하는 메뉴 목록을 조회한다.
	 *
	 * <p>QueryMethod를 이용하여 특정 가격보다 높은 가격의 메뉴들을 조회한다.
	 * JpaRepository에 findByMenuPriceGreaterThan 메서드를 선언하여 사용한다.</p>
	 *
	 * @param menuPrice 기준 가격
	 * @return 기준 가격을 초과하는 메뉴 정보를 담은 MenuDTO 리스트
	 */
	public List<MenuDTO> findMenusByPrice(Integer menuPrice) {
		List<Menu> menuList = menuRepository.findByMenuPriceGreaterThan(menuPrice);

		return menuList.stream()
				.map(this::convertToDTO)
				.toList();
	}

	/* 목차. 6. 메뉴 등록 */
	/**
	 * 새 메뉴를 등록한다.
	 *
	 * <p>전달받은 카테고리 코드의 유효성을 먼저 검사하고,
	 * 조회한 Category 엔티티를 연관 관계로 설정한 뒤 메뉴를 저장한다.</p>
	 *
	 * @param menuDTO 등록할 메뉴 정보
	 * @return 등록된 메뉴 정보를 담은 MenuDTO 객체
	 * @throws IllegalArgumentException 유효하지 않은 카테고리 코드인 경우 발생
	 */
	@Transactional
	public MenuDTO saveMenu(MenuDTO menuDTO) {
		return saveMenu(menuDTO, null);
	}

	@Transactional
	public MenuDTO saveMenu(MenuDTO menuDTO, MultipartFile image) {

		validateAiFields(menuDTO);
		// 카테고리 코드 유효성 검사 겸, 연관 관계에 넣을 Category 엔티티 조회
		Category category = findCategoryOrThrow(menuDTO.getCategoryCode());

		Menu entity = convertToEntity(menuDTO, category);
		if (image != null) entity.setImagePath(imageStorage.store(image));
		Menu savedMenu = menuRepository.saveAndFlush(entity);

		return convertToDTO(savedMenu);
	}

	/* 목차. 7. 메뉴 수정 */
	/**
	 * 메뉴 정보를 수정한다.
	 *
	 * <p>메뉴 코드로 기존 메뉴를 조회하고, 전달받은 DTO의 정보로 메뉴를 수정한다.
	 * 카테고리 코드가 전달된 경우 유효성을 검사한 뒤 연관 관계를 함께 변경한다.</p>
	 *
	 * @param menuCode 수정할 메뉴의 코드
	 * @param menuDTO 수정할 메뉴 정보
	 * @return 수정된 메뉴 정보를 담은 MenuDTO 객체
	 * @throws MenuNotFoundException 메뉴가 존재하지 않는 경우, IllegalArgumentException 유효하지 않은 카테고리 코드인 경우 발생
	 */
	@Transactional
	public MenuDTO updateMenu(int menuCode, MenuDTO menuDTO) {
		return updateMenu(menuCode, menuDTO, null);
	}

	@Transactional
	public MenuDTO updateMenu(int menuCode, MenuDTO menuDTO, MultipartFile image) {
        validateAiFields(menuDTO);

		Menu foundMenu = menuRepository.findById(menuCode)
				.orElseThrow(() -> new MenuNotFoundException("해당 메뉴가 존재하지 않습니다. menuCode=" + menuCode));

		if (menuDTO.getMenuIngredients() != null) foundMenu.setMenuIngredients(menuDTO.getMenuIngredients());
        if (menuDTO.getMenuDescription() != null) foundMenu.setMenuDescription(menuDTO.getMenuDescription());
        foundMenu.setMenuName(menuDTO.getMenuName());
		foundMenu.setMenuPrice(menuDTO.getMenuPrice());
		foundMenu.setOrderableStatus(menuDTO.getOrderableStatus());

		/* 설명. 카테고리 변경은 코드값이 아니라 연관 관계(Category 엔티티) 자체를 바꿔줘야 실제로 반영된다. */
		if (menuDTO.getCategoryCode() > 0) {
			foundMenu.setCategory(findCategoryOrThrow(menuDTO.getCategoryCode()));
		}

		/* 설명. 조회해온 엔티티는 영속 상태이므로, 값만 바꿔두면 트랜잭션이 끝날 때
		 *  변경 감지(dirty checking)에 의해 UPDATE 구문이 자동으로 실행된다.
		 *  즉, 별도의 save() 호출이 필요 없다.
		 * */
		if (image != null) {
			String oldImage = foundMenu.getImagePath();
			foundMenu.setImagePath(imageStorage.store(image));
			imageStorage.deleteAfterCommit(oldImage);
		}
		menuRepository.flush();
		return convertToDTO(foundMenu);
	}

	/* 목차. 8. 메뉴 삭제 */
	/**
	 * 메뉴를 삭제한다.
	 *
	 * <p>메뉴 코드로 메뉴를 조회하고, 해당 메뉴를 삭제한다.
	 * 메뉴가 존재하지 않을 경우 예외가 발생한다.</p>
	 *
	 * @param menuCode 삭제할 메뉴의 코드
	 * @throws MenuNotFoundException 삭제할 메뉴가 존재하지 않는 경우 발생
	 */
	@Transactional
	public void deleteMenu(Integer menuCode) {
		Menu foundMenu = menuRepository.findById(menuCode)
				.orElseThrow(() -> new MenuNotFoundException("해당 메뉴가 존재하지 않습니다. menuCode=" + menuCode));

		menuRepository.delete(foundMenu);
		imageStorage.deleteAfterCommit(foundMenu.getImagePath());
	}
    private void validateAiFields(MenuDTO dto) {
        if (dto.getMenuIngredients() != null && dto.getMenuIngredients().length() > 1000)
            throw new IllegalArgumentException("재료는 1000자 이하로 입력해 주세요.");
        if (dto.getMenuDescription() != null && dto.getMenuDescription().length() > 2000)
            throw new IllegalArgumentException("설명은 2000자 이하로 입력해 주세요.");
    }
}
