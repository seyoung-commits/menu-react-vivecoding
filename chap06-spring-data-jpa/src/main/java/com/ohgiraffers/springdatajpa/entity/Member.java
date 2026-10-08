package com.ohgiraffers.springdatajpa.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "tbl_member", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_kakao", columnNames = {"kakao_app_id", "kakao_user_id"}))
public class Member {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_code")
    private Long memberCode;
    @Column(name = "kakao_app_id", nullable = false)
    private Long kakaoAppId;
    @Column(name = "kakao_user_id", nullable = false)
    private Long kakaoUserId;
    @Column(nullable = false, length = 100)
    private String nickname;
    @Column(name = "profile_image_url", length = 2048)
    private String profileImageUrl;
    @Column(name = "sync_completed", nullable = false)
    private boolean syncCompleted;
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;
    @Column(name = "last_login_at", nullable = false)
    private LocalDateTime lastLoginAt;
    protected Member() {}
    public Long getMemberCode() { return memberCode; }
    public Long getKakaoAppId() { return kakaoAppId; }
    public Long getKakaoUserId() { return kakaoUserId; }
    public String getNickname() { return nickname; }
    public String getProfileImageUrl() { return profileImageUrl; }
    public boolean isSyncCompleted() { return syncCompleted; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getLastLoginAt() { return lastLoginAt; }
}
