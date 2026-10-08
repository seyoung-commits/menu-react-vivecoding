package com.ohgiraffers.springdatajpa.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "tbl_member_term", uniqueConstraints = @UniqueConstraint(
        name = "uk_member_term", columnNames = {"member_code", "term_tag"}))
public class MemberTerm {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "member_term_code")
    private Long memberTermCode;
    @Column(name = "member_code", nullable = false)
    private Long memberCode;
    @Column(name = "term_tag", nullable = false, length = 100)
    private String termTag;
    @Column(name = "required_consent", nullable = false)
    private boolean requiredConsent;
    @Column(nullable = false)
    private boolean agreed;
    @Column(name = "agreed_at", length = 40)
    private String agreedAt;
    protected MemberTerm() {}
    public String getTermTag() { return termTag; }
    public boolean isRequiredConsent() { return requiredConsent; }
    public boolean isAgreed() { return agreed; }
    public String getAgreedAt() { return agreedAt; }
}
