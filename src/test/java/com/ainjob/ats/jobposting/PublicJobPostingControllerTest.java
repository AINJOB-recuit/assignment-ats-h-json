package com.ainjob.ats.jobposting;

import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import com.ainjob.ats.common.ResourceNotFoundException;
import java.time.LocalDateTime;
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
 * 공개 공고 API — 토큰 없이 열려야 한다.
 *
 * <p>이 테스트가 고정하는 것은 <b>지원 흐름의 시작점이 막히지 않는다</b>는 사실이다. 구직자가
 * 공고를 볼 수 없으면 공고 식별자를 알 방법이 없고, 지원 API 만 열려 있어도 쓸 수 없다.
 *
 * <p>동시에 기업용 조회와 <b>보이는 범위가 다르다</b>는 것도 고정한다 — 공개 경로는 모집 중인
 * 공고만 보여 주고, 마감된 공고는 404 다.
 */
@WebMvcTest(PublicJobPostingController.class)
@Import(SecurityConfig.class)
class PublicJobPostingControllerTest {

    private static final String URL = "/api/v1/job-postings";

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private JobPostingService jobPostingService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("비회원도 모집 중인 공고 목록을 볼 수 있다 — 회사명이 함께 내려온다")
    void anonymousCanBrowseList() throws Exception {
        given(jobPostingService.findAllOpen(any())).willReturn(List.of(summary()));

        mockMvc.perform(get(URL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobPostingId").value(9))
                // 여러 기업의 공고가 섞이므로 어느 회사 것인지가 목록에 있어야 한다
                .andExpect(jsonPath("$[0].companyName").value("기업1"))
                .andExpect(jsonPath("$[0].open").value(true));
    }

    @Test
    @DisplayName("비회원도 공고 상세를 볼 수 있다 — 요구조건까지 공개된다")
    void anonymousCanReadDetail() throws Exception {
        given(jobPostingService.findOpenOne(eq(9L), any())).willReturn(detail());

        mockMvc.perform(get(URL + "/9"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPostingId").value(9))
                .andExpect(jsonPath("$.requiredSkills[0].code").value("JAVA"))
                .andExpect(jsonPath("$.requiredCareers[0].years").value(3));
    }

    @Test
    @DisplayName("구직자 토큰으로도 당연히 볼 수 있다")
    void applicantCanBrowse() throws Exception {
        given(jobPostingService.findAllOpen(any())).willReturn(List.of(summary()));

        mockMvc.perform(get(URL).with(applicant(7)))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("기업 담당자 토큰으로도 볼 수 있다 — 공개물이라 회원 구분을 가리지 않는다")
    void companyUserCanBrowse() throws Exception {
        given(jobPostingService.findAllOpen(any())).willReturn(List.of(summary()));

        mockMvc.perform(get(URL).with(companyUser(2, 1, "RECRUITER")))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("마감된 공고 상세는 404 — 목록에서 사라진 것과 상세에서 사라진 것을 일치시킨다")
    void closedPostingIsNotFound() throws Exception {
        given(jobPostingService.findOpenOne(eq(9L), any()))
                .willThrow(new ResourceNotFoundException("job_posting", 9L));

        mockMvc.perform(get(URL + "/9"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("공개 조회는 테넌트를 받지 않는다 — 서비스 시그니처에 companyId 가 없다")
    void publicQueryTakesNoTenant() throws Exception {
        given(jobPostingService.findAllOpen(any())).willReturn(List.of());

        mockMvc.perform(get(URL)).andExpect(status().isOk());

        // 기업용 조회로 새는 경로가 없다는 확인
        verify(jobPostingService, org.mockito.Mockito.never()).findMine(anyLong(), any(), any());
    }

    private static JobPostingSummaryResponse summary() {
        return new JobPostingSummaryResponse(9L, 1L, "기업1", "기업1 백엔드 개발자 채용", "BE",
                true, LocalDateTime.of(2025, 1, 1, 0, 0), null);
    }

    private static JobPostingResponse detail() {
        return new JobPostingResponse(9L, 1L, "기업1", "BE", "백엔드",
                "기업1 백엔드 개발자 채용", "지원 자격 ...", null, null, true,
                List.of(new JobPostingResponse.SkillItem("JAVA", "Java")),
                List.of(new JobPostingResponse.CareerItem("BE", "백엔드", (short) 3)),
                List.of(new JobPostingResponse.EducationItem("BACHELOR", "학사", "컴퓨터공학")));
    }
}
