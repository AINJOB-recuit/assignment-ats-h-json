package com.ainjob.ats.auth;

import com.ainjob.ats.domain.Applicant;
import com.ainjob.ats.domain.CompanyUser;
import java.time.Instant;
import java.util.Optional;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 로그인 → 액세스 토큰 발급. 인증 주체가 둘이므로 진입점도 둘이다.
 *
 * <p>이 클래스가 <b>사칭을 막는 지점</b>이다. 두 가지를 요청이 아니라 인증에 성공한 계정 행에서 읽는다.
 * <ul>
 *   <li>{@code company_id} — 기업1 계정으로 로그인해 기업2 토큰을 받을 경로가 없다.</li>
 *   <li>{@code member_type} — 구직자 계정으로 로그인해 기업 회원 토큰을 받을 경로가 없다.
 *       계정 테이블이 갈려 있으므로 어느 조회에 성공했는지가 곧 회원 구분이다.</li>
 * </ul>
 */
@Service
public class AuthService {

    /** 존재하지 않는 계정에 대해서도 동일한 비용의 해시 비교를 수행하기 위한 더미 값. */
    private static final String NO_SUCH_USER_HASH =
            "{bcrypt}$2a$10$7Iuyc47LUtvKqXVNSDHfFOrQUxH1c2pIVysPePeb99uk4E2tFUOla";

    private final CompanyUserRepository companyUserRepository;
    private final ApplicantAccountRepository applicantAccountRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtEncoder jwtEncoder;
    private final AuthProperties properties;

    public AuthService(CompanyUserRepository companyUserRepository,
                       ApplicantAccountRepository applicantAccountRepository,
                       PasswordEncoder passwordEncoder,
                       JwtEncoder jwtEncoder,
                       AuthProperties properties) {
        this.companyUserRepository = companyUserRepository;
        this.applicantAccountRepository = applicantAccountRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
    }

    /** 기업 담당자 로그인 — 토큰에 {@code company_id} 와 {@code role} 이 함께 실린다. */
    @Transactional(readOnly = true)
    public LoginResponse loginCompanyUser(LoginRequest request) {
        Optional<CompanyUser> found = companyUserRepository.findByEmail(request.email());
        CompanyUser user = verify(found.orElse(null),
                found.map(CompanyUser::getPasswordHash).orElse(null),
                found.map(CompanyUser::isActive).orElse(false),
                request.password());

        String token = issue(JwtClaimsSet.builder()
                .subject(String.valueOf(user.getId()))
                .claim(AuthProperties.MEMBER_TYPE_CLAIM, MemberType.COMPANY_USER.name())
                .claim(AuthProperties.COMPANY_ID_CLAIM, user.getCompanyId())
                .claim(AuthProperties.ROLE_CLAIM, user.getRoleCode())
                .claim(AuthProperties.EMAIL_CLAIM, user.getEmail()));

        return new LoginResponse(token, "Bearer", properties.tokenTtl().toSeconds(),
                user.getId(), user.getCompanyId(), MemberType.COMPANY_USER,
                user.getRoleCode(), user.getName());
    }

    /**
     * 구직자 로그인 — 토큰에 {@code company_id} 도 {@code role} 도 싣지 않는다.
     *
     * <p>구직자는 어느 기업에도 속하지 않으므로 테넌트가 없고, 역할 구분도 없다. 없는 값을 만들어
     * 넣지 않는 것이 곧 격리다 — 이 토큰으로는 {@code /api/v1/companies/**} 어느 경로도 통과하지 못한다.
     */
    @Transactional(readOnly = true)
    public ApplicantLoginResponse loginApplicant(LoginRequest request) {
        Optional<Applicant> found = applicantAccountRepository.findByEmail(request.email());
        Applicant applicant = verify(found.orElse(null),
                found.map(Applicant::getPasswordHash).orElse(null),
                found.map(Applicant::isActive).orElse(false),
                request.password());

        String token = issue(JwtClaimsSet.builder()
                .subject(String.valueOf(applicant.getId()))
                .claim(AuthProperties.MEMBER_TYPE_CLAIM, MemberType.APPLICANT.name())
                .claim(AuthProperties.EMAIL_CLAIM, applicant.getEmail()));

        return new ApplicantLoginResponse(token, "Bearer", properties.tokenTtl().toSeconds(),
                applicant.getId(), MemberType.APPLICANT, applicant.getName());
    }

    /**
     * 계정 유무·활성 여부·비밀번호를 확인한다. 어느 것이 틀렸는지는 응답에서 구분하지 않는다.
     *
     * <p>계정이 없어도 더미 해시로 한 번 비교해서 응답 시간을 맞춘다. 시간 차이로 가입 여부를
     * 알아내는 계정 열거를 막기 위해서다 — 구직자 쪽은 이메일이 곧 개인정보라 더 중요하다.
     */
    private <T> T verify(T account, String passwordHash, boolean active, String rawPassword) {
        if (account == null) {
            passwordEncoder.matches(rawPassword, NO_SUCH_USER_HASH);
            throw new InvalidCredentialsException();
        }
        if (!passwordEncoder.matches(rawPassword, passwordHash) || !active) {
            throw new InvalidCredentialsException();
        }
        return account;
    }

    /** 발급 시각·만료·발급자는 두 주체가 공유한다. 주체별로 다른 것은 클레임뿐이다. */
    private String issue(JwtClaimsSet.Builder claims) {
        Instant issuedAt = Instant.now();
        return jwtEncoder.encode(JwtEncoderParameters.from(
                        JwsHeader.with(MacAlgorithm.HS256).build(),
                        claims.issuer(properties.issuer())
                                .issuedAt(issuedAt)
                                .expiresAt(issuedAt.plus(properties.tokenTtl()))
                                .build()))
                .getTokenValue();
    }
}
