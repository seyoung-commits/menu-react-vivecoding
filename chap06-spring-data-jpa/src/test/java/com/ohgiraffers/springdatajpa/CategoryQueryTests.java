package com.ohgiraffers.springdatajpa;

import com.ohgiraffers.springdatajpa.service.CategoryService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@Transactional(readOnly = true)
class CategoryQueryTests {
    @Autowired
    private CategoryService categoryService;

    @Test
    void allCategoriesUseNativeQueryAndDescendingCodes() {
        var categories = categoryService.findAllCategories();
        assertFalse(categories.isEmpty(), "Expected lecture database categories");
        for (int i = 1; i < categories.size(); i++) {
            assertTrue(categories.get(i - 1).getCategoryCode() > categories.get(i).getCategoryCode());
        }
    }
}
