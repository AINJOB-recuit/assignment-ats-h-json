package com.ainjob.ats.auth;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 구직자 요청의 인증 주체 접근점 — {@link com.ainjob.ats.tenant.TenantContext} 의 구직자판이다.
 *
 * <p>{@code applicant_id} 의 출처는 <b>검증된 JWT 의 subject 하나뿐</b>이다. 경로·본문 어디에서도
 * 읽지 않는다. 이것이 "남의 이름으로 지원하기"를 막는 지점이다 — 지원 요청 본문에 지원자 식별자가
 * 아예 존재하지 않으므로 위조할 대상이 없다.
 *
 * <p>회원 구분까지 확인한다. 인가 규칙({@code SecurityConfig})이 이미 걸러 주지만, 서비스가
 * 컨트롤러 밖에서 재사용될 때를 대비한 이중 방어다.
 */
public final class ApplicantContext {

    private ApplicantContext() {
    }

    /**
     * @return 현재 요청의 applicant_id
     * @throws NotApplicantException 인증 정보가 없거나 구직자 토큰이 아닌 경우
     */
    public static long applicantId() {
        Jwt jwt = applicantJwt();
        String subject = jwt.getSubject();
        if (subject == null || subject.isBlank()) {
            throw new NotApplicantException("토큰에 subject(applicant_id)가 없습니다.");
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new NotApplicantException("토큰 subject 가 applicant_id 가 아닙니다: " + subject);
        }
    }

    /** 응답·감사 표시용 이메일. */
    public static String email() {
        String email = applicantJwt().getClaimAsString(AuthProperties.EMAIL_CLAIM);
        return (email == null || email.isBlank()) ? "unknown" : email;
    }

    private static Jwt applicantJwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new NotApplicantException("인증 정보가 없습니다.");
        }
        Jwt jwt = jwtAuthentication.getToken();
        String memberType = jwt.getClaimAsString(AuthProperties.MEMBER_TYPE_CLAIM);
        if (!MemberType.APPLICANT.name().equals(memberType)) {
            throw new NotApplicantException("구직자 회원 토큰이 아닙니다. member_type=" + memberType);
        }
        return jwt;
    }
}
