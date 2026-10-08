package com.ohgiraffers.springdatajpa.repository;

import com.ohgiraffers.springdatajpa.entity.MemberTerm;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface MemberTermRepository extends JpaRepository<MemberTerm, Long> {
    List<MemberTerm> findByMemberCodeOrderByTermTagAsc(Long memberCode);
    @Modifying
    @Query(value = """
        INSERT INTO tbl_member_term
            (member_code, term_tag, required_consent, agreed, agreed_at)
        VALUES (:member, :tag, :required, :agreed, :agreedAt)
        ON DUPLICATE KEY UPDATE required_consent = VALUES(required_consent),
            agreed = VALUES(agreed), agreed_at = VALUES(agreed_at)
        """, nativeQuery = true)
    void upsert(@Param("member") Long member, @Param("tag") String tag,
            @Param("required") boolean required, @Param("agreed") boolean agreed,
            @Param("agreedAt") String agreedAt);
}
