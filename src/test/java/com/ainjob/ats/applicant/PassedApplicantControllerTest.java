package com.ainjob.ats.applicant;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 요구사항 2 — "company_id 없이 요청하면 어떻게 되는지" 검증.
 *
 * <p>company_id 의 출처는 로그인한 계정의 소속 기업이 실린 JWT 클레임 하나뿐이다. 따라서
 * <ul>
 *   <li>토큰 없는 요청은 <b>401</b> 로 차단되고,</li>
 *   <li>토큰에 company_id 클레임이 없어도 <b>401</b> 로 막히며,</li>
 *   <li>어느 경우에도 조회 계층(Service/Repository)에 <b>도달하지 않는다</b></li>
 * </ul>
 * 는 세 가지를 고정한다. 즉 company_id 필터가 빠진 채로 전체 테넌트를 훑는 실행 경로가 없다.
 */
@WebMvcTest(PassedApplicantController.class)
@Import(SecurityConfig.class)
class PassedApplicantControllerTest {

    private static final String URL = "/api/v1/companies/job-postings/1/passed-applicants";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PassedApplicantService passedApplicantService;

    /** 토큰 서명 검증은 jwt() 후처리기가 대신하므로 디코더는 호출되지 않는다. */
    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("토큰 없이 요청하면 401 TOKEN_REQUIRED, 조회 로직은 호출되지 않는다")
    void withoutToken_returns401_andNeverQueries() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist());

        verify(passedApplicantService, never()).findPassedApplicants(anyLong(), anyLong());
    }

    @Test
    @DisplayName("회원 구분이 없는 토큰은 403 — /companies/** 는 ROLE_COMPANY_USER 부터 요구한다")
    void tokenWithoutMemberType_returns403() throws Exception {
        mockMvc.perform(get(URL).with(jwt()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(passedApplicantService, never()).findPassedApplicants(anyLong(), anyLong());
    }

    @Test
    @DisplayName("구직자 토큰은 403 — 합격자 명단은 기업의 것이다")
    void applicantToken_returns403() throws Exception {
        mockMvc.perform(get(URL).with(applicant(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(passedApplicantService, never()).findPassedApplicants(anyLong(), anyLong());
    }

    @Test
    @DisplayName("권한은 통과해도 company_id 클레임이 없으면 401 TENANT_REQUIRED — 요구사항2-2")
    void tokenWithoutCompanyClaim_returns401() throws Exception {
        // 인가 규칙은 통과하지만(권한 주입) 클레임이 비어 있는 토큰. 격리 조건을 만들 수 없으므로
        // 조회 계층에 도달하기 전에 끊긴다 — company_id 없이 전체 테넌트를 훑는 경로가 없다.
        mockMvc.perform(get(URL).with(jwt()
                        .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));

        verify(passedApplicantService, never()).findPassedApplicants(anyLong(), anyLong());
    }

    @Test
    @DisplayName("company_id 클레임이 0 이하면 401 TENANT_REQUIRED")
    void tokenWithNonPositiveCompanyId_returns401() throws Exception {
        mockMvc.perform(get(URL).with(jwt()
                        .jwt(token -> token
                                .claim("member_type", "COMPANY_USER")
                                .claim("company_id", 0))
                        .authorities(new SimpleGrantedAuthority("ROLE_COMPANY_USER"))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TENANT_REQUIRED"));
    }

    @Test
    @DisplayName("다른 회사 공고를 조회하면 404 — 403 이면 그 번호가 실재한다는 사실이 새어 나간다")
    void otherTenantJobPosting_returns404() throws Exception {
        given(passedApplicantService.findPassedApplicants(1L, 1L))
                .willThrow(new CrossTenantAccessException("job_posting", 1L, 1L));

        mockMvc.perform(get(URL).with(companyUser(42, 1, "RECRUITER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"))
                // 소유권을 암시하는 내부 메시지가 그대로 나가면 안 된다.
                .andExpect(jsonPath("$.message").value("요청한 리소스를 찾을 수 없습니다."));
    }

    @Test
    @DisplayName("로그인한 계정의 company_id 로 조회된다")
    void withValidToken_returns200() throws Exception {
        given(passedApplicantService.findPassedApplicants(1L, 1L)).willReturn(
                new PassedApplicantsResponse(1L, "기업1", 1L, "기업1 백엔드 개발자 채용", 1,
                        List.of(new PassedApplicant(3L, 7L, "문지후", "h.json248@gmail.com",
                                "BE", 5, (short) 3, "HIRED", "최종합격"))));

        mockMvc.perform(get(URL).with(companyUser(42, 1, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].applicantName").value("문지후"));
    }

    @Test
    @DisplayName("VIEWER 역할도 조회는 가능하다 (역할 인가는 쓰기 작업에만 건다)")
    void viewerCanRead() throws Exception {
        given(passedApplicantService.findPassedApplicants(1L, 1L)).willReturn(
                new PassedApplicantsResponse(1L, "기업1", 1L, "기업1 백엔드 개발자 채용", 0, List.of()));

        mockMvc.perform(get(URL).with(companyUser(43, 1, "VIEWER")))
                .andExpect(status().isOk());
    }

}
