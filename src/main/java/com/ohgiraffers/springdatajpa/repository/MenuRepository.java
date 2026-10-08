package com.ohgiraffers.springdatajpa.repository;

import com.ohgiraffers.springdatajpa.entity.Menu;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MenuRepository extends JpaRepository<Menu, Integer> {
	
	/* 전달 받은 가격을 초과하는 메뉴의 목록을 조회하는 메소드 */
	List<Menu> findByMenuPriceGreaterThan(Integer menuPrice);

	/* 전달 받은 가격을 초과하는 메뉴의 목록을 가격 순으로 조회하는 메소드 */
	List<Menu> findByMenuPriceGreaterThanOrderByMenuPrice(Integer menuPrice);
	
	/* 전달 받은 가격을 초과하는 메뉴의 목록을 전달 받는 정렬 기준으로 조회하는 메소드 */
	List<Menu> findByMenuPriceGreaterThan(Integer menuPrice, Sort sort);
}