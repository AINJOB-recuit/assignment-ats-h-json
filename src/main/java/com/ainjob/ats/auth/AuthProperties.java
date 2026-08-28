package com.ainjob.ats.auth;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 인증 설정.
 *
 * @param jwtSecret HS256 서명 키. 비워 두면 기동 시 임의 키를 생성한다(개발 편의).
 *                  서명 키는 클라이언트가 알 필요가 없으므로 저장소에 기본값을 두지 않는다.
 * @param issuer    JWT iss 클레임
 * @param tokenTtl  액세스 토큰 유효기간
 */
@ConfigurationProperties(prefix = "ainjob.auth")
public record AuthProperties(String jwtSecret, String issuer, Duration tokenTtl) {

    /**
     * 회원 구분 클레임 — 구직자({@link MemberType#APPLICANT})인지 기업 담당자
     * ({@link MemberType#COMPANY_USER})인지. 모든 토큰에 반드시 실린다.
     */
    public static final String MEMBER_TYPE_CLAIM = "member_type";
    /** 테넌트 클레임. 인증과 격리를 잇는 유일한 접점이다. 기업 담당자 토큰에만 있다. */
    public static final String COMPANY_ID_CLAIM = "company_id";
    /** 역할 클레임. ROLE_ 접두사를 붙여 권한으로 변환한다. 기업 담당자 토큰에만 있다. */
    public static final String ROLE_CLAIM = "role";
    /** 화면 표시·감사 로그용 이메일 클레임. */
    public static final String EMAIL_CLAIM = "email";

    public AuthProperties {
        if (issuer == null || issuer.isBlank()) {
            issuer = "ainjob-ats";
        }
        if (tokenTtl == null || tokenTtl.isZero() || tokenTtl.isNegative()) {
            tokenTtl = Duration.ofHours(1);
        }
    }
}
