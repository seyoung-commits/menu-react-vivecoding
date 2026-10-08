package com.ohgiraffers.springdatajpa.repository;

import com.ohgiraffers.springdatajpa.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CategoryRepository extends JpaRepository<Category, Integer> {


    //JPQL
//    @Query(
//            value = "SELECT c FROM Category c ORDER BY c.categoryCode DESC",
//            nativeQuery = true
//    )
    @Query(
            value = "SELECT c.* FROM tbl_category c ORDER BY c.category_code DESC",
            nativeQuery = true
    )
    List<Category> findAllByName();
    
} 