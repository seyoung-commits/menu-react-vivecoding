package com.ohgiraffers.springdatajpa.entity;

import jakarta.persistence.*;

import java.util.List;

@Entity
@Table(name = "tbl_category")
public class Category {

    @Id
    @Column(name = "category_code")
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int categoryCode;

    @Column(name = "category_name")
    private String categoryName;

    /* 설명. 셀프 참조 (상위 카테고리)
     *  Menu의 category와 같은 이유로, ref_category_code 컬럼도 연관 관계 필드 하나로만 매핑한다.
     *  상위 카테고리가 없는 최상위 카테고리(식사, 음료, 디저트)는 이 값이 null이다.
     * */
    @ManyToOne
    @JoinColumn(name = "ref_category_code")
    private Category parentCategory;

    /* 설명. 역방향 참조 (하위 카테고리 목록)
     *  mappedBy는 "외래키를 관리하는 쪽은 상대편의 parentCategory 필드"라는 의미다.
     *  즉 이 목록에 값을 담아도 DB에는 반영되지 않는다. (읽기 용도)
     * */
    @OneToMany(mappedBy = "parentCategory")
    private List<Category> childCategories;

    /* 설명. 해당 카테고리에 속한 메뉴 목록 (마찬가지로 읽기 용도) */
    @OneToMany(mappedBy = "category")
    private List<Menu> menus;

    public Category() {}

    public Category(int categoryCode, String categoryName, Category parentCategory) {
        this.categoryCode = categoryCode;
        this.categoryName = categoryName;
        this.parentCategory = parentCategory;
    }

    public int getCategoryCode() {
        return categoryCode;
    }

    public void setCategoryCode(int categoryCode) {
        this.categoryCode = categoryCode;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Category getParentCategory() {
        return parentCategory;
    }

    public void setParentCategory(Category parentCategory) {
        this.parentCategory = parentCategory;
    }

    public List<Category> getChildCategories() {
        return childCategories;
    }

    public void setChildCategories(List<Category> childCategories) {
        this.childCategories = childCategories;
    }

    public List<Menu> getMenus() {
        return menus;
    }

    public void setMenus(List<Menu> menus) {
        this.menus = menus;
    }

    @Override
    public String toString() {
        return "Category{" +
                "categoryCode=" + categoryCode +
                ", categoryName='" + categoryName + '\'' +
                // TODO 연관 객체는 코드만 출력하여 양방향 관계의 무한 참조를 방지한다.
                ", parentCategory=" + (parentCategory != null ? parentCategory.getCategoryCode() : null) +
                ", childCategories=" + (childCategories != null ? childCategories.stream().map(Category::getCategoryCode).toList() : null) +
                ", menus=" + (menus != null ? menus.stream().map(Menu::getMenuCode).toList() : null) +
                '}';
    }
}
