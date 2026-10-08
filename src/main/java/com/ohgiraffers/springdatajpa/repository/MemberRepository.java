package com.ohgiraffers.springdatajpa.repository;

import com.ohgiraffers.springdatajpa.entity.Member;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.util.Optional;
import jakarta.persistence.LockModeType;

public interface MemberRepository extends JpaRepository<Member, Long> {
    Optional<Member> findByKakaoAppIdAndKakaoUserId(Long appId, Long userId);

    // 같은 회원의 여러 브라우저에서도 담기·수량 변경·결제 후 정리를 순서대로 처리한다.
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from Member m where m.memberCode = :memberCode")
    Optional<Member> lockByMemberCode(@Param("memberCode") Long memberCode);

    // MySQL의 원자적 upsert + UNIQUE 제약으로 동시 가입도 중복 회원을 만들지 않는다.
    @Modifying
    @Query(value = """
        INSERT INTO tbl_member
            (kakao_app_id, kakao_user_id, nickname, profile_image_url, sync_completed, created_at, last_login_at)
        VALUES (:appId, :userId, :nickname, :profileImageUrl, :synced, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
        ON DUPLICATE KEY UPDATE nickname = VALUES(nickname),
            profile_image_url = VALUES(profile_image_url),
            last_login_at = CURRENT_TIMESTAMP,
            sync_completed = (sync_completed OR VALUES(sync_completed))
        """, nativeQuery = true)
    void upsert(@Param("appId") Long appId, @Param("userId") Long userId,
            @Param("nickname") String nickname, @Param("profileImageUrl") String profileImageUrl,
            @Param("synced") boolean synced);
}
