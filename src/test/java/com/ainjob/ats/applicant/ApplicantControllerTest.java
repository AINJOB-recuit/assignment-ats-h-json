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
 * 구직자 회원가입/본인 조회 API의 인가와 요청 검증.
 *
 * <p>검증하는 규칙은 둘이다 — <b>가입은 비인증</b>(가입 전에는 토큰이 있을 수 없다)이고,
 * <b>조회는 본인만</b>(경로의 식별자를 토큰 subject 와 대조한다)이다.
 * 규칙의 앞단은 {@link SecurityConfig}, 본인 대조는 서비스가 맡는다.
 */
@WebMvcTest(ApplicantController.class)
@Import(SecurityConfig.class)
class ApplicantControllerTest {

    private static final String URL = "/api/v1/applicants";
    private static final String BODY = """
            {
              "name": "문지후",
              "email": "moon@example.com",
              "password": "applicant1234",
              "educations": [{"degreeCode":"BACHELOR","majorName":"컴퓨터공학","schoolName":"한국대"}],
              "careers": [{"positionCode":"BE","companyName":"이전회사",
                           "startAt":"2019-01-01T00:00:00","skillCodes":["JAVA","SPRINGBOOT"]}]
            }
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ApplicantService applicantService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("회원가입은 토큰 없이 된다 — 201 + Location")
    void anyoneCanSignUp() throws Exception {
        given(applicantService.register(any(), any())).willReturn(response());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/applicants/21"))
                .andExpect(jsonPath("$.applicantId").value(21))
                .andExpect(jsonPath("$.careerYearsByPosition[0].positionCode").value("BE"))
                .andExpect(jsonPath("$.careerYearsByPosition[0].years").value(6));
    }

    @Test
    @DisplayName("응답에 계정 정보(비밀번호)는 실리지 않는다")
    void responseNeverCarriesCredentials() throws Exception {
        given(applicantService.register(any(), any())).willReturn(response());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());
    }

    @Test
    @DisplayName("비밀번호가 없으면 400 VALIDATION_ERROR — 로그인할 수 없는 계정이 생기지 않는다")
    void passwordIsRequired() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"문지후","email":"moon@example.com"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(applicantService, never()).register(any(), any());
    }

    @Test
    @DisplayName("비밀번호가 8자 미만이면 400")
    void shortPasswordIsRejected() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"문지후","email":"moon@example.com","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(applicantService, never()).register(any(), any());
    }

    @Test
    @DisplayName("이메일 형식이 틀리면 400 VALIDATION_ERROR")
    void invalidEmailIsRejected() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"문지후","email":"not-an-email","password":"applicant1234"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(applicantService, never()).register(any(), any());
    }

    @Test
    @DisplayName("경력 종료일이 시작일보다 빠르면 400 — DB 제약으로는 못 막는 규칙")
    void reversedCareerPeriodIsRejected() throws Exception {
        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"문지후","email":"moon@example.com","password":"applicant1234",
                                 "careers":[{"positionCode":"BE","companyName":"이전회사",
                                             "startAt":"2020-01-01T00:00:00",
                                             "endAt":"2019-01-01T00:00:00"}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));

        verify(applicantService, never()).register(any(), any());
    }

    @Test
    @DisplayName("경력 시작일이 미래여도 등록은 된다 — 입사 예정 경력을 미리 넣는 것은 정상 입력이다")
    void futureCareerStartIsAccepted() throws Exception {
        // 음수 개월수는 입력을 거부해서가 아니라 계산 규칙으로 막는다(Applicant.careerYearsOf).
        // 여기서는 '검증에 걸리지 않고 서비스까지 도달한다'만 확인한다.
        given(applicantService.register(any(), any())).willReturn(response());

        mockMvc.perform(post(URL).contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"문지후","email":"moon@example.com","password":"applicant1234",
                                 "careers":[{"positionCode":"BE","companyName":"미래회사",
                                             "startAt":"2099-01-01T00:00:00"}]}
                                """))
                .andExpect(status().isCreated());

        verify(applicantService).register(any(), any());
    }

    @Test
    @DisplayName("구직자는 본인 프로필을 조회할 수 있다 — 토큰 subject 가 서비스로 전달된다")
    void applicantCanReadOwnProfile() throws Exception {
        given(applicantService.findOwnProfile(anyLong(), anyLong(), any())).willReturn(response());

        mockMvc.perform(get(URL + "/21").with(applicant(21)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(21));

        verify(applicantService).findOwnProfile(eq(21L), eq(21L), any());
    }

    @Test
    @DisplayName("경로의 식별자가 아니라 토큰 subject 가 본인 판정의 기준이다")
    void tokenSubjectIsPassedNotPathValue() throws Exception {
        given(applicantService.findOwnProfile(anyLong(), anyLong(), any())).willReturn(response());

        mockMvc.perform(get(URL + "/999").with(applicant(21)))
                .andExpect(status().isOk());

        // 컨트롤러는 경로 값을 신뢰하지 않고 둘 다 넘긴다. 999 != 21 이면 서비스가 403 을 던진다.
        verify(applicantService).findOwnProfile(eq(999L), eq(21L), any());
    }

    @Test
    @DisplayName("기업 담당자 토큰으로는 이 경로에 도달할 수 없다 — 403, 회원 구분에서 걸린다")
    void companyUserIsForbiddenOnApplicantPath() throws Exception {
        mockMvc.perform(get(URL + "/21").with(companyUser(2, 1, "RECRUITER")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(applicantService, never()).findOwnProfile(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("토큰 없이 프로필을 조회하면 401")
    void anonymousCannotReadProfile() throws Exception {
        mockMvc.perform(get(URL + "/21"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));

        verify(applicantService, never()).findOwnProfile(anyLong(), anyLong(), any());
    }

    private static ApplicantResponse response() {
        return new ApplicantResponse(21L, "문지후", "moon@example.com", null, null,
                List.of(), List.of(),
                List.of(new ApplicantResponse.CareerYears("BE", "백엔드", 6)));
    }

}
