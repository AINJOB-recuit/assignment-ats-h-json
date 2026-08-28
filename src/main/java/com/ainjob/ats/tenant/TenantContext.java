package com.ainjob.ats.tenant;

import com.ainjob.ats.auth.AuthProperties;
import com.ainjob.ats.auth.MemberType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * 요청 단위 인증 주체 접근점.
 *
 * <p>3계층 격리 중 <b>① Presentation 계층</b>에 해당한다(README 7장).
 * {@code company_id} 의 출처는 <b>검증된 JWT의 클레임 하나뿐</b>이며, 그 클레임은
 * 로그인 시 계정 행에서 채워진 값이다. 헤더·경로·쿼리 어디에서도 읽지 않는다.
 *
 * <p>별도 ThreadLocal 을 두지 않고 {@link SecurityContextHolder} 를 그대로 쓴다.
 * 이미 요청 스코프 ThreadLocal 이고, 필터 체인이 정리까지 책임지므로 누수 위험이 없다.
 *
 * <p><b>기업 담당자 토큰 전용이다.</b> 구직자 토큰에는 {@code company_id} 클레임이 애초에 없으므로
 * 여기를 통과할 수 없다. 구직자 쪽 접근점은
 * {@link com.ainjob.ats.auth.ApplicantContext} 로 따로 있다.
 */
public final class TenantContext {

    private TenantContext() {
    }

    /**
     * @return 현재 요청의 company_id
     * @throws TenantRequiredException 인증 정보가 없거나 토큰에 company_id 클레임이 없는 경우
     */
    public static long companyId() {
        Jwt jwt = jwt();
        Object claim = jwt.getClaim(AuthProperties.COMPANY_ID_CLAIM);
        if (!(claim instanceof Number companyId)) {
            throw new TenantRequiredException(
                    "토큰에 유효한 " + AuthProperties.COMPANY_ID_CLAIM + " 클레임이 없습니다.");
        }
        if (companyId.longValue() <= 0) {
            throw new TenantRequiredException(
                    AuthProperties.COMPANY_ID_CLAIM + " 클레임이 올바르지 않습니다: " + companyId);
        }
        return companyId.longValue();
    }

    /**
     * @return 처리자의 company_user_id — {@code stage.created_by} FK 값
     * @throws TenantRequiredException subject 가 없거나 숫자가 아닌 경우
     */
    public static long companyUserId() {
        String subject = jwt().getSubject();
        if (subject == null || subject.isBlank()) {
            throw new TenantRequiredException("토큰에 subject(company_user_id)가 없습니다.");
        }
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new TenantRequiredException("토큰 subject 가 company_user_id 가 아닙니다: " + subject);
        }
    }

    /** 응답·감사 표시용 이메일. 상태 전이 응답의 {@code changedBy} 필드에 그대로 실린다. */
    public static String email() {
        String email = jwt().getClaimAsString(AuthProperties.EMAIL_CLAIM);
        return (email == null || email.isBlank()) ? "unknown" : email;
    }

    /**
     * 검증된 토큰을 꺼내며 회원 구분까지 확인한다.
     *
     * <p>인가 규칙({@code SecurityConfig})이 이미 구직자 토큰을 걸러 내지만, 서비스가 다른 경로에서
     * 재사용될 때를 대비한 이중 방어다. 격리 조건의 출처를 한 군데로 좁혀 두면 나중에 경로가 늘어도
     * 검사를 빠뜨릴 수 없다.
     */
    private static Jwt jwt() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            throw new TenantRequiredException("인증 정보가 없습니다.");
        }
        Jwt jwt = jwtAuthentication.getToken();
        String memberType = jwt.getClaimAsString(AuthProperties.MEMBER_TYPE_CLAIM);
        if (!MemberType.COMPANY_USER.name().equals(memberType)) {
            throw new TenantRequiredException(
                    "기업 회원 토큰이 아닙니다. member_type=" + memberType);
        }
        return jwt;
    }
}
