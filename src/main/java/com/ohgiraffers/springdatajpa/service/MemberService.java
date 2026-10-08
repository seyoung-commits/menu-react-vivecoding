package com.ohgiraffers.springdatajpa.service;

import com.ohgiraffers.springdatajpa.dto.*;
import com.ohgiraffers.springdatajpa.entity.Member;
import com.ohgiraffers.springdatajpa.exception.KakaoAuthException;
import com.ohgiraffers.springdatajpa.repository.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.net.URI;
import java.util.*;

@Service
public class MemberService {
    private final MemberRepository members;
    private final MemberTermRepository terms;
    public MemberService(MemberRepository members, MemberTermRepository terms) {
        this.members = members;
        this.terms = terms;
    }

    @Transactional
    public MemberDTO login(Long appId, KakaoUserResponse user,
            List<KakaoTermsResponse.ServiceTerm> consent, boolean synced) {
        members.upsert(appId, user.id(), nickname(user), profileImageUrl(user), synced);
        Member member = members.findByKakaoAppIdAndKakaoUserId(appId, user.id())
                .orElseThrow(() -> new KakaoAuthException("MEMBER_SAVE_FAILED", 500,
                        "회원 정보를 저장하지 못했습니다."));
        if (synced) {
            for (var term : consent) {
                terms.upsert(member.getMemberCode(), term.tag(),
                        Boolean.TRUE.equals(term.required()),
                        Boolean.TRUE.equals(term.agreed()), term.agreedAt());
            }
        }
        return dto(member);
    }

    @Transactional(readOnly = true)
    public Optional<MemberDTO> find(Long memberCode) {
        return members.findById(memberCode).map(this::dto);
    }

    private MemberDTO dto(Member member) {
        var consent = terms.findByMemberCodeOrderByTermTagAsc(member.getMemberCode())
                .stream().map(t -> new MemberDTO.TermDTO(t.getTermTag(),
                        t.isRequiredConsent(), t.isAgreed(), t.getAgreedAt())).toList();
        return new MemberDTO(member.getMemberCode(), member.getNickname(), member.getProfileImageUrl(),
                member.getKakaoAppId(), member.getKakaoUserId(), member.isSyncCompleted(),
                member.getCreatedAt(), member.getLastLoginAt(), consent);
    }

    static String nickname(KakaoUserResponse user) {
        if (user.kakaoAccount() == null || user.kakaoAccount().profile() == null
                || user.kakaoAccount().profile().nickname() == null) return "카카오 사용자";
        String value = user.kakaoAccount().profile().nickname().strip();
        StringBuilder name = new StringBuilder();
        value.codePoints().filter(c -> !Character.isISOControl(c)).limit(100)
                .forEach(name::appendCodePoint);
        return name.isEmpty() ? "카카오 사용자" : name.toString();
    }

    static String profileImageUrl(KakaoUserResponse user) {
        if (user.kakaoAccount() == null || user.kakaoAccount().profile() == null) return null;
        var profile = user.kakaoAccount().profile();
        for (String candidate : new String[] {profile.profileImageUrl(), profile.thumbnailImageUrl()}) {
            String safe = safeImageUrl(candidate);
            if (safe != null) return safe;
        }
        return null;
    }

    private static String safeImageUrl(String value) {
        if (value == null || value.isBlank()) return null;
        value = value.strip();
        // 카카오 응답의 http CDN 주소도 HTTPS로 표시한다. 파일을 서버에 다운로드하지 않는다.
        if (value.regionMatches(true, 0, "http://", 0, 7)) value = "https://" + value.substring(7);
        if (value.length() > 2048) return null;
        try {
            URI uri = URI.create(value);
            if (!"https".equalsIgnoreCase(uri.getScheme()) || uri.getHost() == null
                    || uri.getRawUserInfo() != null || (uri.getPort() != -1 && uri.getPort() != 443)) return null;
            String host = uri.getHost().toLowerCase(Locale.ROOT);
            boolean kakaoCdn = host.equals("kakaocdn.net") || host.endsWith(".kakaocdn.net")
                    || host.equals("kakao.com") || host.endsWith(".kakao.com")
                    || host.equals("daumcdn.net") || host.endsWith(".daumcdn.net");
            return kakaoCdn ? value : null;
        } catch (IllegalArgumentException e) { return null; }
    }
}
