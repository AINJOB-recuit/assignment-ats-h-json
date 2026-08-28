package com.ainjob.ats.stage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static com.ainjob.ats.TestTokens.applicant;
import static com.ainjob.ats.TestTokens.companyUser;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.auth.SecurityConfig;
import java.time.OffsetDateTime;
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
 * 상태 전이 API의 역할 인가 검증.
 *
 * <p>조회는 모든 역할에 열려 있지만 <b>쓰기(상태 전이)는 OWNER / RECRUITER 만</b> 가능하다.
 * 규칙은 {@link SecurityConfig} 한 곳에 있고, 여기서는 그 규칙이 실제로 적용되는지를 고정한다.
 */
@WebMvcTest(StageTransitionController.class)
@Import(SecurityConfig.class)
class StageTransitionControllerTest {

    private static final String URL = "/api/v1/companies/applications/11/stage";
    private static final String BODY = """
            {"transition":"FORWARD","toStageTypeId":3}
            """;

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private StageTransitionService stageTransitionService;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    @DisplayName("RECRUITER 는 상태를 전이할 수 있다")
    void recruiterCanTransition() throws Exception {
        given(stageTransitionService.transition(anyLong(), anyLong(), any(), anyLong(), anyString()))
                .willReturn(new StageTransitionResponse(11L, (short) 2, "INTERVIEW", (short) 3, "HIRED",
                        TransitionType.FORWARD, 45L, 4L, "recruiter@company2.com",
                        OffsetDateTime.now(), true));

        mockMvc.perform(patch(URL).with(companyUser(4, 2, "RECRUITER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedBy").value("recruiter@company2.com"))
                .andExpect(jsonPath("$.changedByUserId").value(4));
    }

    @Test
    @DisplayName("OWNER 도 상태를 전이할 수 있다")
    void ownerCanTransition() throws Exception {
        given(stageTransitionService.transition(anyLong(), anyLong(), any(), anyLong(), anyString()))
                .willReturn(new StageTransitionResponse(11L, (short) 2, "INTERVIEW", (short) 3, "HIRED",
                        TransitionType.FORWARD, 45L, 1L, "owner@company1.com",
                        OffsetDateTime.now(), true));

        mockMvc.perform(patch(URL).with(companyUser(1, 1, "OWNER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("VIEWER 는 403 ACCESS_DENIED — 서비스 호출조차 되지 않는다")
    void viewerIsForbidden() throws Exception {
        mockMvc.perform(patch(URL).with(companyUser(5, 2, "VIEWER"))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(stageTransitionService, never()).transition(anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("구직자 토큰은 403 — 본인 전형 상태를 스스로 바꿀 수 없다")
    void applicantCannotTransition() throws Exception {
        mockMvc.perform(patch(URL).with(applicant(7))
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        verify(stageTransitionService, never()).transition(anyLong(), anyLong(), any(), anyLong(), anyString());
    }

    @Test
    @DisplayName("토큰 없이 전이 시도하면 401")
    void withoutTokenIsUnauthorized() throws Exception {
        mockMvc.perform(patch(URL).contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));

        verify(stageTransitionService, never()).transition(anyLong(), anyLong(), any(), anyLong(), anyString());
    }

}
