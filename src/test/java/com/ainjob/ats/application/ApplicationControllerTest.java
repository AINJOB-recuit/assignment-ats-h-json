package com.ainjob.ats.application;

import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import java.time.LocalDateTime;
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
 * 지원 API의 인가와 요청 검증.
 *
 * <p>지원은 <b>구직자 본인만</b> 한다. 기업 담당자는 아무리 높은 역할이어도 이 경로를 통과하지
 * 못한다 — 회원 구분({@code ROLE_APPLICANT})에서 걸리기 때문이다.
 *
 * <p>가장 중요한 검증은 마지막 두 개다. 요청 본문에 지원자 식별자를 넣어도 무시되며, 서비스에는
 * <b>토큰 subject 만</b> 전달된다. 대리 지원이 막히는 것이 아니라 표현될 수 없다는 사실의 확인이다.
 */
@WebMvcTest(ApplicationController.class)
@Import(SecurityConfig.class)
class ApplicationControllerTest {

    private static final String URL = "/api/v1/job-postings/1/applications";
    private static final String BODY = """
            {"reason": "백엔드 6년차입니다. 지원합니다."}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicationService applicationService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("구직자는 지원할 수 있다 — 201 + Location + 초기 이력 stageId")
    void applicantCanApply() throws Exception {
        given(applicationService.apply(anyLong(), anyLong(), any(), anyString(), any()))
                .willReturn(response());

        mockMvc.perform(post(URL).with(applicant(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/applications/16"))
                .andExpect(jsonPath("$.applicationId").value(16))
                // 지원과 동시에 이력 1행이 쌓였다는 사실이 응답으로 드러난다
                .andExpect(jsonPath("$.stageCode").value("APPLIED"))
                .andExpect(jsonPath("$.stageId").value(45))
                // 첫 단계를 만든 행위자는 담당자가 아니라 지원자 본인이다
                .andExpect(jsonPath("$.createdBy").value("applicant7@example.com"));
    }

    @Test
    @DisplayName("reason 은 없어도 된다 — 지원에 필수인 값은 경로(공고)와 토큰(지원자)뿐이다")
    void reasonIsOptional() throws Exception {
        given(applicationService.apply(anyLong(), anyLong(), any(), anyString(), any()))
                .willReturn(response());

        mockMvc.perform(post(URL).with(applicant(7))
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    @Test
    @DisplayName("RECRUITER 는 403 ACCESS_DENIED — 담당자가 대신 지원할 수 없다")
    void recruiterCannotApplyOnBehalf() throws Exception {
        mockMvc.perform(post(URL).with(companyUser(2, 1, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(applicationService, never()).apply(anyLong(), anyLong(), any(), anyString(), any());
    }

    @Test
    @DisplayName("OWNER 도 마찬가지로 403 — 역할이 아니라 회원 구분에서 걸린다")
    void ownerCannotApplyEither() throws Exception {
        mockMvc.perform(post(URL).with(companyUser(1, 1, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(applicationService, never()).apply(anyLong(), anyLong(), any(), anyString(), any());
    }

    @Test
    @DisplayName("토큰 없이 지원하면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));

        verify(applicationService, never()).apply(anyLong(), anyLong(), any(), anyString(), any());
    }

    @Test
    @DisplayName("지원자는 토큰에서만 나온다 — 본문에 applicantId 를 넣어도 무시된다")
    void applicantIdInBodyIsIgnored() throws Exception {
        given(applicationService.apply(anyLong(), anyLong(), any(), anyString(), any()))
                .willReturn(response());

        mockMvc.perform(post(URL).with(applicant(7))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"applicantId": 999, "reason": "남의 번호로 지원 시도"}
                                """))
                .andExpect(status().isCreated());

        // 본문의 999 가 아니라 토큰의 7 이 전달된다. 요청 형식에 지원자 식별자가 없으므로
        // 그 값은 역직렬화 단계에서 버려진다.
        verify(applicationService).apply(eq(1L), eq(7L), any(), eq("applicant7@example.com"), any());
    }

    @Test
    @DisplayName("공고 식별자는 경로에서, 지원자 식별자는 토큰에서 온다")
    void jobPostingFromPathApplicantFromToken() throws Exception {
        given(applicationService.apply(anyLong(), anyLong(), any(), anyString(), any()))
                .willReturn(response());

        mockMvc.perform(post("/api/v1/job-postings/42/applications").with(applicant(13))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated());

        verify(applicationService).apply(eq(42L), eq(13L), any(), eq("applicant13@example.com"), any());
    }

    private static ApplicationCreatedResponse response() {
        return new ApplicationCreatedResponse(16L, 1L, 1L, "기업1 백엔드 개발자 채용",
                7L, "문지후", (short) 1, "APPLIED", "서류접수", 45L,
                "applicant7@example.com", LocalDateTime.now());
    }
}
