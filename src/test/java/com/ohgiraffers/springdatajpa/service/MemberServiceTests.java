package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.entity.Member;
import com.ohgiraffers.springdatajpa.repository.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class MemberServiceTests {
    final MemberRepository members = mock(MemberRepository.class);
    final MemberTermRepository terms = mock(MemberTermRepository.class);
    final MemberService service = new MemberService(members, terms);
    @Test void optionalNicknameHasFallbackAndTruncatesUnicodeByCodePoint() {
        assertEquals("카카오 사용자", MemberService.nickname(new KakaoUserResponse(1L, null)));
        String emoji = "😀".repeat(101);
        var user = new KakaoUserResponse(1L, new KakaoUserResponse.KakaoAccount(new KakaoUserResponse.Profile(emoji, null, null)));
        String nickname = MemberService.nickname(user);
        assertEquals(100, nickname.codePointCount(0, nickname.length()));
    }
    @Test void repeatedLoginUsesExistingMemberAndStoresConsentWhenSynced() {
        Member member = mock(Member.class);
        when(member.getMemberCode()).thenReturn(7L);
        when(member.getKakaoAppId()).thenReturn(123L);
        when(member.getKakaoUserId()).thenReturn(42L);
        when(member.getNickname()).thenReturn("카카오 사용자");
        when(members.findByKakaoAppIdAndKakaoUserId(123L, 42L)).thenReturn(Optional.of(member));
        when(terms.findByMemberCodeOrderByTermTagAsc(7L)).thenReturn(List.of());
        var user = new KakaoUserResponse(42L, null);
        assertEquals(7L, service.login(123L, user, List.of(), false).memberCode());
        var consent = List.of(new KakaoTermsResponse.ServiceTerm("menu_terms_20261005", true, true, "2026-10-05T00:00:00Z"));
        assertEquals(7L, service.login(123L, user, consent, true).memberCode());
        verify(members).upsert(123L, 42L, "카카오 사용자", null, false);
        verify(members).upsert(123L, 42L, "카카오 사용자", null, true);
        verify(terms).upsert(7L, "menu_terms_20261005", true, true, "2026-10-05T00:00:00Z");
    }

    KakaoUserResponse profile(String image, String thumbnail) {
        return new KakaoUserResponse(42L,
                new KakaoUserResponse.KakaoAccount(new KakaoUserResponse.Profile("김세영", image, thumbnail)));
    }
    @Test void profileUrlUsesHttpsAndFallsBackToThumbnailWhenNeeded() {
        assertEquals("https://k.kakaocdn.net/dn/test/photo.jpg",
                MemberService.profileImageUrl(profile("http://k.kakaocdn.net/dn/test/photo.jpg", null)));
        assertEquals("https://k.kakaocdn.net/dn/test/thumb.jpg",
                MemberService.profileImageUrl(profile(null, "https://k.kakaocdn.net/dn/test/thumb.jpg")));
        assertNull(MemberService.profileImageUrl(new KakaoUserResponse(42L, null)));
    }
    @Test void profileUrlRejectsScriptsCredentialsLocalAddressesAndUntrustedHosts() {
        for (String url : List.of("javascript:alert(1)", "data:image/png;base64,test", "https://localhost/a",
                "https://k.kakaocdn.net.attacker.test/a", "https://secret@k.kakaocdn.net/a", "https://k.kakaocdn.net:8080/a"))
            assertNull(MemberService.profileImageUrl(profile(url, null)));
    }
    @Test void repeatLoginUpdatesProviderNicknameAndPhotoWithoutCreatingAnotherMember() {
        Member member = mock(Member.class);
        when(member.getMemberCode()).thenReturn(7L);
        when(member.getNickname()).thenReturn("김세영");
        when(member.getProfileImageUrl()).thenReturn("https://k.kakaocdn.net/dn/new/photo.jpg");
        when(members.findByKakaoAppIdAndKakaoUserId(123L, 42L)).thenReturn(Optional.of(member));
        when(terms.findByMemberCodeOrderByTermTagAsc(7L)).thenReturn(List.of());
        var first = profile("https://k.kakaocdn.net/dn/old/photo.jpg", null);
        var updated = profile("https://k.kakaocdn.net/dn/new/photo.jpg", null);
        assertEquals(7L, service.login(123L, first, List.of(), false).memberCode());
        var result = service.login(123L, updated, List.of(), false);
        assertEquals(7L, result.memberCode());
        assertEquals("김세영", result.nickname());
        assertEquals("https://k.kakaocdn.net/dn/new/photo.jpg", result.profileImageUrl());
        verify(members).upsert(123L, 42L, "김세영", "https://k.kakaocdn.net/dn/old/photo.jpg", false);
        verify(members).upsert(123L, 42L, "김세영", "https://k.kakaocdn.net/dn/new/photo.jpg", false);
    }
}
