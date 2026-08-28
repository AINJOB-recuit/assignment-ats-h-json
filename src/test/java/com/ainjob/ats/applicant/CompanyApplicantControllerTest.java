package com.ainjob.ats.applicant;

import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기업 담당자의 지원자 열람 API.
 *
 * <p>이 경로가 닫는 구멍은 이렇다 — 지원자는 글로벌 풀이라 그 자체로는 테넌트가 없다. 그래서
 * 지원자 단독 경로로 열어 두면 아무 회사나 전체 지원자의 개인정보를 훑을 수 있다.
 * <b>공고를 앵커로 삼아</b> "우리 회사 공고에 지원한 사람"으로 범위를 좁히는 것이 요점이고,
 * 경로 구조가 그 근거를 그대로 드러낸다.
 */
@WebMvcTest(CompanyApplicantController.class)
@Import(SecurityConfig.class)
class CompanyApplicantControllerTest {

    private static final String URL = "/api/v1/companies/job-postings/1/applicants/7";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyApplicantService companyApplicantService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("담당자는 자기 공고에 지원한 지원자를 조회할 수 있다 — 테넌트는 토큰에서 온다")
    void companyUserCanReadApplicantOfOwnPosting() throws Exception {
        given(companyApplicantService.findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any()))
                .willReturn(response());

        mockMvc.perform(get(URL).with(companyUser(2, 1, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(7))
                .andExpect(jsonPath("$.careerYearsByPosition[0].years").value(6));

        // companyId 는 경로가 아니라 토큰(company_id=1)에서 나온다
        verify(companyApplicantService).findApplicantOfJobPosting(eq(1L), eq(1L), eq(7L), any());
    }

    @Test
    @DisplayName("VIEWER 도 조회할 수 있다 — 역할 인가는 쓰기 작업에만 건다")
    void viewerCanRead() throws Exception {
        given(companyApplicantService.findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any()))
                .willReturn(response());

        mockMvc.perform(get(URL).with(companyUser(5, 1, "VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("다른 회사 공고면 404 — 지원하지 않은 지원자와 같은 응답이다")
    void otherTenantPosting_returns404() throws Exception {
        given(companyApplicantService.findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new CrossTenantAccessException("job_posting", 1L, 2L));

        mockMvc.perform(get(URL).with(companyUser(4, 2, "RECRUITER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("우리 공고에 지원하지 않은 지원자는 404 — 403 이면 존재 여부가 새어 나간다")
    void applicantWhoDidNotApply_returns404() throws Exception {
        given(companyApplicantService.findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any()))
                .willThrow(new ResourceNotFoundException("application", 7L));

        mockMvc.perform(get(URL).with(companyUser(2, 1, "RECRUITER")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("구직자 토큰은 403 — 이 경로는 기업 전용이다")
    void applicantToken_returns403() throws Exception {
        mockMvc.perform(get(URL).with(applicant(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(companyApplicantService, never())
                .findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("토큰 없이 조회하면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(get(URL))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));

        verify(companyApplicantService, never())
                .findApplicantOfJobPosting(anyLong(), anyLong(), anyLong(), any());
    }

    private static ApplicantResponse response() {
        return new ApplicantResponse(7L, "문지후", "h.json248@gmail.com", null, null,
                List.of(), List.of(),
                List.of(new ApplicantResponse.CareerYears("BE", "백엔드", 6)));
    }
}
