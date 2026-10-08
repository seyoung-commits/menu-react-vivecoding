package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.CategoryDTO;
import com.ohgiraffers.springdatajpa.entity.Category;
import com.ohgiraffers.springdatajpa.exception.CategoryNotFoundException;
import com.ohgiraffers.springdatajpa.repository.CategoryRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // @Autowired를 작성하지 않아도 자동 적용됨을 잊지 말자.
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    /* 설명. Category 엔티티를 CategoryDTO로 변환한다. (MenuService의 convertToDTO와 같은 역할)
     *  도메인마다 다루는 타입이 다르므로 각 서비스에 같은 이름으로 정의해두고 사용한다.
     *  ----------------------------------------------------------------------------------
     *  최상위 카테고리(식사, 음료, 디저트)는 상위 카테고리가 없으므로
     *  parentCategory가 null인지 반드시 확인한 뒤 접근해야 한다.
     * */
    private CategoryDTO convertToDTO(Category category) {

        Category parent = category.getParentCategory();

        return new CategoryDTO(
                category.getCategoryCode(),
                category.getCategoryName(),
                parent != null ? parent.getCategoryCode() : null,
                parent != null ? parent.getCategoryName() : null
        );
    }

    // 모든 카테고리 조회
    public List<CategoryDTO> findAllCategories() {
        List<Category> categories = categoryRepository.findAllByName();

        return categories.stream()
                .map(this::convertToDTO)
                .toList();
    }

    // 카테고리 코드로 카테고리 조회
    public CategoryDTO findCategoryByCode(int categoryCode) {
        Category category = categoryRepository.findById(categoryCode)
                .orElseThrow(() -> new CategoryNotFoundException("해당 카테고리가 존재하지 않습니다. categoryCode=" + categoryCode));

        return convertToDTO(category);
    }
}
