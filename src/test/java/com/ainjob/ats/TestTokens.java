package com.ainjob.ats;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;

import com.ainjob.ats.auth.MemberType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;

/**
 * 컨트롤러 슬라이스 테스트용 토큰 픽스처.
 *
 * <p>인증 주체가 둘이 되면서 "어떤 토큰인가"가 거의 모든 인가 테스트의 전제가 됐다. 파일마다
 * 같은 헬퍼를 복사해 두면 클레임 구성이 조금씩 어긋나고, 그 어긋남이 곧 통과하면 안 될 테스트를
 * 통과시킨다. 그래서 한 곳에 모은다.
 *
 * <p>{@code jwt()} 후처리기는 필터 체인을 거치지 않으므로 {@code SecurityConfig} 의 클레임 → 권한
 * 변환기가 적용되지 않는다. 여기서 권한을 직접 주입하되 <b>변환기가 만들어 낼 것과 같은 조합</b>을
 * 넣는다. 변환 자체가 맞는지는 {@code AtsApiIntegrationTest} 가 실제 발급 토큰으로 검증한다.
 */
public final class TestTokens {

    private TestTokens() {
    }

    /** 구직자 토큰 — 권한 하나. company_id 도 role 도 없다. */
    public static JwtRequestPostProcessor applicant(long applicantId) {
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(applicantId))
                        .claim("member_type", MemberType.APPLICANT.name())
                        .claim("email", applicantEmail(applicantId)))
                .authorities(new SimpleGrantedAuthority("ROLE_APPLICANT"));
    }

    /** 기업 담당자 토큰 — 권한 둘. 회원 구분 + 역할. */
    public static JwtRequestPostProcessor companyUser(long userId, long companyId, String role) {
        return jwt()
                .jwt(token -> token
                        .subject(String.valueOf(userId))
                        .claim("member_type", MemberType.COMPANY_USER.name())
                        .claim("company_id", companyId)
                        .claim("role", role)
                        .claim("email", role.toLowerCase() + "@company" + companyId + ".com"))
                .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_USER"),
                        new SimpleGrantedAuthority("ROLE_" + role));
    }

    public static String applicantEmail(long applicantId) {
        return "applicant" + applicantId + "@example.com";
    }
}
