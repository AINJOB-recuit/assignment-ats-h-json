package com.ainjob.ats.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 인증/인가 필터 체인.
 *
 * <p>설계 요점 세 가지.
 * <ol>
 *   <li><b>경로가 곧 대상 독자다.</b> {@code /api/v1/companies/**} 는 기업 담당자 전용,
 *       접두어가 없는 경로는 구직자와 비회원의 것이다. 그래서 "이 URL 을 누가 부르는가"를
 *       경로만 보고 알 수 있고, 인가 규칙도 그 구조를 그대로 따라간다.</li>
 *   <li><b>company_id 는 어느 경로에도 없다.</b> 기업 경로가 {@code /companies/{id}} 가 아니라
 *       {@code /companies} 인 이유다 — 테넌트는 토큰 클레임 하나에서만 나오므로, 남의 회사를
 *       지정해 볼 여지가 URL 문법 수준에서 존재하지 않는다.</li>
 *   <li><b>인가는 두 겹이다.</b> 먼저 회원 구분({@link MemberType}), 그다음 역할.
 *       구직자 토큰은 역할 권한을 아예 갖지 못하므로 기업용 쓰기 규칙을 통과할 수 없고,
 *       기업 토큰은 {@code ROLE_APPLICANT} 가 없으므로 구직자 전용 경로를 통과할 수 없다.</li>
 * </ol>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** 기업 쪽 쓰기(등록·마감·상태전이)가 가능한 역할. VIEWER 는 조회만 가능하다. */
    private static final String[] WRITE_ROLES = {"OWNER", "RECRUITER"};

    /** 회원 구분도 ROLE_ 권한으로 변환된다 — 아래 {@link #jwtAuthenticationConverter()} 참고. */
    private static final String COMPANY_USER = MemberType.COMPANY_USER.name();
    private static final String APPLICANT = MemberType.APPLICANT.name();

    @Bean
    PasswordEncoder passwordEncoder() {
        // {bcrypt} / {noop} 등 접두사로 알고리즘을 식별한다. 저장된 해시를 나중에 교체할 수 있다.
        // 구직자와 기업 담당자가 같은 인코더를 공유한다 — 계정 테이블만 다를 뿐 저장 방식은 같다.
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * JWT 클레임 → 권한 변환. <b>토큰 하나가 권한을 최대 두 개 만든다.</b>
     *
     * <pre>
     * 기업 담당자 : member_type=COMPANY_USER, role=RECRUITER
     *              → [ROLE_COMPANY_USER, ROLE_RECRUITER]
     * 구직자      : member_type=APPLICANT   (role 클레임 없음)
     *              → [ROLE_APPLICANT]
     * </pre>
     *
     * <p>회원 구분을 역할과 같은 형식으로 실어 두면 인가 규칙에서 {@code hasRole} 하나로 둘 다
     * 표현할 수 있다. 구직자에게 역할 클레임을 주지 않는 것이 핵심이다 — 없는 권한은 흉내 낼 수 없다.
     */
    @Bean
    JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(jwt -> {
            List<GrantedAuthority> authorities = new ArrayList<>(2);
            addRole(authorities, jwt.getClaimAsString(AuthProperties.MEMBER_TYPE_CLAIM));
            addRole(authorities, jwt.getClaimAsString(AuthProperties.ROLE_CLAIM));
            return authorities;
        });
        return converter;
    }

    private static void addRole(List<GrantedAuthority> authorities, String value) {
        if (value != null && !value.isBlank()) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + value));
        }
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder,
                                            JwtAuthenticationConverter jwtAuthenticationConverter,
                                            ObjectMapper objectMapper) throws Exception {
        RestAuthenticationEntryPoint entryPoint = new RestAuthenticationEntryPoint(objectMapper);

        return http
                .csrf(csrf -> csrf.disable())
                .cors(cors -> cors.disable())
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // ── 공개 : 로그인 · 구직자 회원가입 · 공고 열람 ────────────────────
                        // 잡포털의 기본 사용자는 구직자이므로 접두어 없는 /auth/login 이 구직자 몫이다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/login").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/v1/auth/companies/login").permitAll()
                        // 이력서 등록 = 구직자 회원가입. 가입 전이므로 토큰이 있을 수 없다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/applicants").permitAll()
                        // 공고는 누구나 본다 — 볼 수 없으면 지원할 공고를 고를 수 없다.
                        .requestMatchers(HttpMethod.GET, "/api/v1/job-postings").permitAll()
                        .requestMatchers(HttpMethod.GET, "/api/v1/job-postings/*").permitAll()

                        // ── 구직자 전용 ───────────────────────────────────────────────
                        // 본인 확인은 컨트롤러가 토큰 subject 와 대조한다(여기서는 회원 구분까지만).
                        .requestMatchers(HttpMethod.GET, "/api/v1/applicants/*").hasRole(APPLICANT)
                        // 지원은 본인만 한다. 지원자 식별자가 요청 본문에 없으므로 대리 지원이 불가능하다.
                        .requestMatchers(HttpMethod.POST, "/api/v1/job-postings/*/applications").hasRole(APPLICANT)

                        // ── 기업 : 쓰기 (역할 인가) ────────────────────────────────────
                        .requestMatchers(HttpMethod.POST, "/api/v1/companies/job-postings")
                            .hasAnyRole(WRITE_ROLES)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/companies/job-postings/*/close")
                            .hasAnyRole(WRITE_ROLES)
                        .requestMatchers(HttpMethod.PATCH, "/api/v1/companies/applications/*/stage")
                            .hasAnyRole(WRITE_ROLES)

                        // ── 기업 : 조회 (VIEWER 포함) ─────────────────────────────────
                        // 회원 구분 게이트. 구직자 토큰은 ROLE_COMPANY_USER 가 없어 여기서 403 이다.
                        // 테넌트 격리는 이 뒤에 쿼리/서비스가 따로 건다.
                        .requestMatchers("/api/v1/companies/**").hasRole(COMPANY_USER)

                        .anyRequest().authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter))
                        .authenticationEntryPoint(entryPoint))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(entryPoint)
                        .accessDeniedHandler(new RestAccessDeniedHandler(objectMapper)))
                .build();
    }
}
