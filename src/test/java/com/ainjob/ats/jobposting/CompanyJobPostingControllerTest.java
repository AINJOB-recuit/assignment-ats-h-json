package com.ainjob.ats.jobposting;

import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 기업 공고 API의 인가 규칙 — 등록·마감은 OWNER / RECRUITER, 조회는 기업 전 역할.
 *
 * <p>두 가지가 중요하다. <b>어느 요청에도 company_id 가 없다</b>(경로가 {@code /companies/{id}} 가
 * 아니라 {@code /companies} 이므로 소속 기업은 토큰에서만 나온다). 그리고 <b>구직자는 역할과
 * 무관하게 막힌다</b> — 회원 구분에서 먼저 걸리기 때문이다.
 */
@WebMvcTest(CompanyJobPostingController.class)
@Import(SecurityConfig.class)
class CompanyJobPostingControllerTest {

    private static final String URL = "/api/v1/companies/job-postings";
    private static final String BODY = """
            {
              "title": "기업1 백엔드 개발자 채용",
              "positionCode": "BE",
              "requiredSkillCodes": ["JAVA", "SPRINGBOOT"],
              "requiredCareers": [{"positionCode":"BE","years":3}],
              "requiredEducations": [{"degreeCode":"BACHELOR","majorName":"컴퓨터공학"}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostingService jobPostingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("RECRUITER 는 공고를 등록할 수 있다 — 201 + Location + 요구조건 반환")
    void recruiterCanCreate() throws Exception {
        given(jobPostingService.create(anyLong(), any(), any())).willReturn(response(true));

        mockMvc.perform(post(URL).with(companyUser(2, 1, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/companies/job-postings/9"))
                .andExpect(jsonPath("$.jobPostingId").value(9))
                .andExpect(jsonPath("$.requiredSkills[0].code").value("JAVA"))
                .andExpect(jsonPath("$.requiredCareers[0].years").value(3));
    }

    @Test
    @DisplayName("VIEWER 는 공고를 등록할 수 없다 — 403, 서비스 미호출")
    void viewerCannotCreate() throws Exception {
        mockMvc.perform(post(URL).with(companyUser(5, 2, "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(jobPostingService, never()).create(anyLong(), any(), any());
    }

    @Test
    @DisplayName("VIEWER 도 공고 목록은 조회할 수 있다")
    void viewerCanRead() throws Exception {
        given(jobPostingService.findMine(anyLong(), any(), any())).willReturn(List.of());

        mockMvc.perform(get(URL).with(companyUser(5, 2, "VIEWER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VIEWER 는 공고를 마감할 수 없다 — 403")
    void viewerCannotClose() throws Exception {
        mockMvc.perform(patch(URL + "/9/close").with(companyUser(5, 2, "VIEWER")))
                .andExpect(status().isForbidden());

        verify(jobPostingService, never()).close(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("RECRUITER 는 공고를 마감할 수 있다 — open=false 로 응답")
    void recruiterCanClose() throws Exception {
        given(jobPostingService.close(anyLong(), anyLong(), any())).willReturn(response(false));

        mockMvc.perform(patch(URL + "/9/close").with(companyUser(2, 1, "RECRUITER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    @DisplayName("구직자는 공고를 등록할 수 없다 — 403, 회원 구분에서 걸린다")
    void applicantCannotCreate() throws Exception {
        mockMvc.perform(post(URL).with(applicant(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(jobPostingService, never()).create(anyLong(), any(), any());
    }

    @Test
    @DisplayName("구직자는 기업 공고 목록도 볼 수 없다 — 조회조차 ROLE_COMPANY_USER 를 요구한다")
    void applicantCannotReadCompanyList() throws Exception {
        mockMvc.perform(get(URL).with(applicant(7)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(jobPostingService, never()).findMine(anyLong(), any(), any());
    }

    @Test
    @DisplayName("title 이 없으면 400 VALIDATION_ERROR")
    void titleIsRequired() throws Exception {
        mockMvc.perform(post(URL).with(companyUser(2, 1, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"positionCode":"BE"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(jobPostingService, never()).create(anyLong(), any(), any());
    }

    @Test
    @DisplayName("토큰 없이 등록하면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));
    }

    private static JobPostingResponse response(boolean open) {
        return new JobPostingResponse(9L, 1L, "기업1", "BE", "백엔드",
                "기업1 백엔드 개발자 채용", null, null, null, open,
                List.of(new JobPostingResponse.SkillItem("JAVA", "Java")),
                List.of(new JobPostingResponse.CareerItem("BE", "백엔드", (short) 3)),
                List.of(new JobPostingResponse.EducationItem("BACHELOR", "학사", "컴퓨터공학")));
    }
}
