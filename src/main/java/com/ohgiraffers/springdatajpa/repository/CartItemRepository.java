package com.ohgiraffers.springdatajpa.repository;

import com.ohgiraffers.springdatajpa.entity.CartItem;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.*;

public interface CartItemRepository extends JpaRepository<CartItem, Long> {
    List<CartItem> findByMemberCodeOrderByCartItemCodeAsc(Long memberCode);
    Optional<CartItem> findByMemberCodeAndMenuCode(Long memberCode, int menuCode);
    Optional<CartItem> findByCartItemCodeAndMemberCode(Long itemCode, Long memberCode);
    long countByMemberCode(Long memberCode);

    // 승인 당시와 같은 항목만 정리한다. 결제 중 수정·삭제 후 다시 담은 항목은 유지한다.
    @Modifying
    @Query("delete from CartItem c where c.memberCode = :member and c.cartItemCode = :item and c.version = :version")
    int deletePurchased(@Param("member") Long member, @Param("item") Long item,
            @Param("version") long version);
}
