package com.ainjob.ats;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.applicant.JpaPassedApplicantFinder;
import com.ainjob.ats.applicant.NativeSqlPassedApplicantFinder;
import com.ainjob.ats.applicant.PassedApplicant;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 실제 DB(03_AINJOB_schema.sql + 03_AINJOB_dummy.sql 적재본)를 대상으로 하는 통합 테스트.
 *
 * <p>기대 결과 3케이스를 API 레벨에서 재현하고,
 * 로그인 → 인증 호출 → 회원 구분 → 역할 인가까지 end-to-end 로 검증한다.
 * 실행하려면 스키마·더미가 적재된 DB 와 접속 정보를 담은 {@code config/application-local.yml} 이 있어야 한다.
 *
 * <p>여기서만 확인할 수 있는 것이 하나 있다 — <b>클레임 → 권한 변환</b>이다. 슬라이스 테스트의
 * {@code jwt()} 후처리기는 필터 체인을 건너뛰므로 권한을 직접 주입하지만, 이 테스트는 로그인
 * 엔드포인트가 실제로 발급한 토큰을 헤더에 실어 보낸다. 그래서 "구직자 토큰으로 기업 경로를
 * 부르면 403" 같은 규칙이 설정대로 동작하는지가 여기서만 진짜로 증명된다.
 *
 * <p>상태 전이 테스트는 {@link Transactional} 로 롤백되므로 더미 데이터가 오염되지 않는다.
 */
@SpringBootTest
@AutoConfigureMockMvc
// 서명 키·메일 설정 등 테스트 전용 값은 src/test/resources/application.yml 에 모아 둔다.
// 그 파일이 클래스패스에서 메인 설정을 통째로 대신하므로 여기서 따로 덮어쓰지 않는다.
class AtsApiIntegrationTest {

    /** 더미 기업 계정 5개의 공통 비밀번호. */
    private static final String PASSWORD = "ainjob1234!";
    /** 더미 구직자 14명의 공통 비밀번호. */
    private static final String APPLICANT_PASSWORD = "applicant1234";

    private static final String OWNER_1 = "owner@company1.com";
    private static final String RECRUITER_1 = "recruiter@company1.com";
    private static final String VIEWER_1 = "viewer@company1.com";
    private static final String RECRUITER_2 = "recruiter@company2.com";
    private static final String VIEWER_2 = "viewer@company2.com";

    /** 문지후(applicant_id=7). 기업1 공고에는 최종합격, 기업2 공고에는 면접 단계다. */
    private static final String APPLICANT_7 = "h.json248@gmail.com";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // 두 전략의 구현을 나란히 주입한다. 설정으로 하나만 빈으로 만들지 않은 이유가 이것이다 —
    // 같은 데이터에 대해 둘을 직접 대조할 수 있어야 한다.
    @Autowired
    private NativeSqlPassedApplicantFinder nativeSqlFinder;

    @Autowired
    private JpaPassedApplicantFinder jpaFinder;

    // ─────────────────────────── 로그인 ───────────────────────────

    @Test
    @DisplayName("기업 로그인 — 계정의 소속 기업과 역할이 토큰에 실린다")
    void companyLogin() throws Exception {
        mockMvc.perform(companyLoginRequest(RECRUITER_2, PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.companyUserId").value(4))
                .andExpect(jsonPath("$.companyId").value(2))
                .andExpect(jsonPath("$.memberType").value("COMPANY_USER"))
                .andExpect(jsonPath("$.role").value("RECRUITER"));
    }

    @Test
    @DisplayName("구직자 로그인 — companyId 도 role 도 없다. 없는 값을 만들어 싣지 않는다")
    void applicantLogin() throws Exception {
        mockMvc.perform(applicantLoginRequest(APPLICANT_7, APPLICANT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.applicantId").value(7))
                .andExpect(jsonPath("$.memberType").value("APPLICANT"))
                .andExpect(jsonPath("$.name").value("문지후"))
                .andExpect(jsonPath("$.companyId").doesNotExist())
                .andExpect(jsonPath("$.role").doesNotExist());
    }

    @Test
    @DisplayName("로그인 — company_id 는 요청에 없다. 기업1 계정으로는 기업2 토큰을 만들 수 없다")
    void tenantCannotBeSpoofed() throws Exception {
        String body = mockMvc.perform(companyLoginRequest(RECRUITER_1, PASSWORD))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        // 기업1 계정으로 로그인하면 어떤 방법으로도 companyId=2 가 나오지 않는다.
        JsonNode json = objectMapper.readTree(body);
        org.assertj.core.api.Assertions.assertThat(json.get("companyId").asLong()).isEqualTo(1L);
    }

    @Test
    @DisplayName("로그인 — 회원 구분은 계정 테이블이 정한다. 구직자 계정으로 기업 로그인은 401")
    void memberTypeCannotBeChosen() throws Exception {
        // 같은 자격증명을 기업 경로로 보내도 company_user 테이블에는 없으므로 계정을 찾지 못한다.
        mockMvc.perform(companyLoginRequest(APPLICANT_7, APPLICANT_PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));

        // 반대 방향도 마찬가지다.
        mockMvc.perform(applicantLoginRequest(RECRUITER_1, PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그인 — 비밀번호 불일치는 401")
    void loginWithWrongPassword() throws Exception {
        mockMvc.perform(companyLoginRequest(RECRUITER_1, "wrong-password"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("로그인 — 없는 계정도 같은 401 (계정 열거 방지)")
    void loginWithUnknownEmail() throws Exception {
        mockMvc.perform(companyLoginRequest("nobody@nowhere.com", PASSWORD))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    @DisplayName("위조된 토큰으로 조회하면 401 INVALID_TOKEN")
    void forgedToken() throws Exception {
        mockMvc.perform(get(PASSED_APPLICANTS.formatted(1))
                        .header(HttpHeaders.AUTHORIZATION, "Bearer eyJhbGciOiJIUzI1NiJ9.forged.signature"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_TOKEN"));
    }

    @Test
    @DisplayName("토큰 없이 요청 — 401 TOKEN_REQUIRED, 데이터 미노출")
    void withoutToken() throws Exception {
        mockMvc.perform(get(PASSED_APPLICANTS.formatted(1)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"))
                .andExpect(jsonPath("$.items").doesNotExist());
    }

    // ──────────────────── 회원 구분 격리 (경로 단위) ────────────────────

    @Test
    @DisplayName("구직자 토큰으로 기업 경로를 부르면 403 — 실제 발급 토큰으로 확인한다")
    void applicantTokenCannotReachCompanyPaths() throws Exception {
        mockMvc.perform(asApplicant(get(PASSED_APPLICANTS.formatted(1)), APPLICANT_7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(asApplicant(get(COMPANY_JOB_POSTINGS), APPLICANT_7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(asApplicant(post(COMPANY_JOB_POSTINGS), APPLICANT_7)
                        .contentType(MediaType.APPLICATION_JSON).content(JOB_POSTING_BODY))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @DisplayName("기업 토큰으로 구직자 경로를 부르면 403 — 담당자가 대신 지원할 수 없다")
    void companyTokenCannotReachApplicantPaths() throws Exception {
        mockMvc.perform(as(post(PUBLIC_JOB_POSTINGS + "/1/applications"), RECRUITER_1)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));

        mockMvc.perform(as(get(APPLICANTS + "/7"), RECRUITER_1))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    // ─────────────────────── 공개 공고 조회 ───────────────────────

    @Test
    @DisplayName("공개 공고 목록 — 토큰 없이 열린다. 여러 기업의 공고가 섞여 나온다")
    void publicJobPostingListIsOpen() throws Exception {
        mockMvc.perform(get(PUBLIC_JOB_POSTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(4)))
                .andExpect(jsonPath("$[*].companyId")
                        .value(org.hamcrest.Matchers.hasItems(1, 2)))
                .andExpect(jsonPath("$[*].open")
                        .value(org.hamcrest.Matchers.everyItem(org.hamcrest.Matchers.is(true))));
    }

    @Test
    @DisplayName("공개 공고 상세 — 토큰 없이 요구조건까지 볼 수 있다. 이게 있어야 지원할 공고를 고른다")
    void publicJobPostingDetailIsOpen() throws Exception {
        mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPostingId").value(1))
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.requiredSkills").isArray())
                .andExpect(jsonPath("$.requiredCareers").isArray())
                .andExpect(jsonPath("$.requiredEducations").isArray());
    }

    @Test
    @Transactional
    @DisplayName("공개 상세 — 마감된 공고는 404. 담당자에게는 같은 공고가 그대로 보인다")
    void closedPostingHiddenFromPublicButVisibleToOwner() throws Exception {
        long jobPostingId = createJobPosting();

        mockMvc.perform(as(patch(COMPANY_JOB_POSTINGS + "/" + jobPostingId + "/close"), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/" + jobPostingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));

        // 같은 공고가 주인에게는 보인다 — 지난 공고도 관리 대상이기 때문이다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/" + jobPostingId), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    @Transactional
    @DisplayName("모집 기간 — close_dt 가 지나면 담당자가 마감하지 않아도 공개 영역에서 사라진다")
    void postingPastCloseDateDisappearsFromPublicArea() throws Exception {
        // is_open 은 1 그대로다. 기간만 지났다 — 스케줄러가 없으면 실제로 생기는 상태다.
        long jobPostingId = createJobPosting("2025-01-01T00:00:00", "2025-12-31T00:00:00");

        mockMvc.perform(get(PUBLIC_JOB_POSTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jobPostingId == %d)]".formatted(jobPostingId))
                        .isEmpty());

        mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/" + jobPostingId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @Transactional
    @DisplayName("모집 기간 — close_dt 가 지난 공고에는 지원도 접수되지 않는다 (409)")
    void cannotApplyToPostingPastCloseDate() throws Exception {
        String email = "past-close@example.com";
        registerApplicant(email);
        long jobPostingId = createJobPosting("2025-01-01T00:00:00", "2025-12-31T00:00:00");

        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/" + jobPostingId + "/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_CLOSED"));
    }

    @Test
    @Transactional
    @DisplayName("모집 기간 — 담당자에게는 기간이 끝난 공고가 ?open=false 로 잡히고 open 값도 false 다")
    void postingPastCloseDateIsClosedForOwnerToo() throws Exception {
        long jobPostingId = createJobPosting("2025-01-01T00:00:00", "2025-12-31T00:00:00");

        // 상세: is_open 플래그는 1 이지만 응답의 open 은 실효 상태(false)를 싣는다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/" + jobPostingId), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        // ?open=true 에는 없고
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "?open=true"), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jobPostingId == %d)]".formatted(jobPostingId)).isEmpty());

        // ?open=false 에는 있다 — 두 쿼리가 정확한 여집합이어야 한다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "?open=false"), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jobPostingId == %d)]".formatted(jobPostingId)).isNotEmpty());
    }

    @Test
    @Transactional
    @DisplayName("모집 기간 — open_dt 가 미래인 예약 공고는 아직 공개되지 않는다")
    void postingBeforeOpenDateIsNotPublicYet() throws Exception {
        long jobPostingId = createJobPosting("2099-01-01T00:00:00", null);

        mockMvc.perform(get(PUBLIC_JOB_POSTINGS))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.jobPostingId == %d)]".formatted(jobPostingId)).isEmpty());

        mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/" + jobPostingId))
                .andExpect(status().isNotFound());

        // 주인에게는 보인다 — 예약해 둔 공고를 관리할 수 있어야 한다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/" + jobPostingId), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));
    }

    @Test
    @Transactional
    @DisplayName("모집 기간 — close_dt 가 없거나 미래면 그대로 모집 중이다 (조건이 과하게 걸리지 않는다)")
    void postingWithinPeriodStaysOpen() throws Exception {
        long noCloseDate = createJobPosting("2025-01-01T00:00:00", null);
        long futureCloseDate = createJobPosting("2025-01-01T00:00:00", "2099-12-31T00:00:00");

        for (long jobPostingId : new long[] {noCloseDate, futureCloseDate}) {
            mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/" + jobPostingId))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.open").value(true));
        }
    }

    // ─────────────────── 구직자 본인 프로필 조회 ───────────────────

    @Test
    @DisplayName("구직자는 본인 프로필만 볼 수 있다 — 남의 번호는 403")
    void applicantSeesOnlyOwnProfile() throws Exception {
        mockMvc.perform(asApplicant(get(APPLICANTS + "/7"), APPLICANT_7))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(7))
                .andExpect(jsonPath("$.name").value("문지후"));

        mockMvc.perform(asApplicant(get(APPLICANTS + "/2"), APPLICANT_7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWN_PROFILE"));
    }

    @Test
    @DisplayName("존재하지 않는 번호도 본인이 아니면 403 — 404 와 갈리면 가입자 수를 셀 수 있다")
    void nonExistentIdIsAlsoForbidden() throws Exception {
        mockMvc.perform(asApplicant(get(APPLICANTS + "/999999"), APPLICANT_7))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("NOT_OWN_PROFILE"));
    }

    // ───────────── 기업의 지원자 열람 (공고를 앵커로 삼는 격리) ─────────────

    @Test
    @DisplayName("담당자는 자기 공고에 지원한 지원자만 볼 수 있다")
    void companyReadsApplicantOfOwnPosting() throws Exception {
        // 이서윤(2)은 기업1 공고 1의 지원자다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/1/applicants/2"), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.applicantId").value(2))
                .andExpect(jsonPath("$.careerYearsByPosition").isArray());
    }

    @Test
    @DisplayName("우리 공고에 지원하지 않은 지원자는 404 — 존재 여부가 새지 않는다")
    void applicantWhoDidNotApplyIsNotFound() throws Exception {
        // 김철수(8)는 실재하지만 기업2 공고 3의 지원자다. 기업1 공고 1로는 볼 수 없다.
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/1/applicants/8"), RECRUITER_1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("남의 회사 공고를 앵커로 삼으면 404 — 테넌트 필터가 그 공고를 아예 안 보이게 한다")
    void crossTenantApplicantLookup() throws Exception {
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/3/applicants/8"), RECRUITER_1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("VIEWER 도 지원자를 열람할 수 있다 — 역할 인가는 쓰기 작업에만 건다")
    void viewerCanReadApplicant() throws Exception {
        mockMvc.perform(as(get(COMPANY_JOB_POSTINGS + "/1/applicants/2"), VIEWER_1))
                .andExpect(status().isOk());
    }

    // ─────────────────────── 합격자 필터 API ───────────────────────

    @Test
    @DisplayName("기업1 BE 합격자 — 문지후 포함 3명")
    void company1BackendPassedApplicants() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(1)), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[*].applicantName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("이서윤", "한예진", "문지후")));
    }

    @Test
    @DisplayName("기업2 BE 합격자 — 문지후 미포함(기업2에서는 면접 단계)")
    void company2BackendPassedApplicants() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(3)), RECRUITER_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2))
                .andExpect(jsonPath("$.items[*].applicantName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("김철수", "류태현")))
                .andExpect(jsonPath("$.items[*].applicantName")
                        .value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem("문지후"))));
    }

    @Test
    @DisplayName("기업2 FE 합격자 — 3명")
    void company2FrontendPassedApplicants() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(4)), RECRUITER_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(3))
                .andExpect(jsonPath("$.items[*].applicantName")
                        .value(org.hamcrest.Matchers.containsInAnyOrder("안서연", "황도윤", "권유진")));
    }

    @Test
    @DisplayName("VIEWER 도 조회는 가능하다")
    void viewerCanRead() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(3)), VIEWER_2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(2));
    }

    @Test
    @DisplayName("기업1 계정으로 기업2 공고 조회 — 404 (없는 공고와 구분되지 않는다)")
    void crossTenantJobPosting() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(3)), RECRUITER_1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("없는 공고 조회 — 404")
    void unknownJobPosting() throws Exception {
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(999999)), RECRUITER_1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ─────────────────────── 상태 전이 API ───────────────────────

    @Test
    @Transactional
    @DisplayName("상태 전이 — RECRUITER 가 면접(2)에서 최종합격(3)으로 진행, created_by 는 계정 FK")
    void forwardTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(11)), RECRUITER_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":3,"reason":"면접 통과"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromStageTypeId").value(2))
                .andExpect(jsonPath("$.toStageTypeId").value(3))
                .andExpect(jsonPath("$.changedByUserId").value(4))
                .andExpect(jsonPath("$.changedBy").value(RECRUITER_2));

        // 담당자가 만든 이력이므로 created_by 가 채워진다 — 구직자가 만든 첫 단계와 대비된다.
        Long createdBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM stage WHERE application_id = 11 ORDER BY stage_id DESC LIMIT 1",
                Long.class);
        org.assertj.core.api.Assertions.assertThat(createdBy).isEqualTo(4L);
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — VIEWER 는 403 ACCESS_DENIED (역할 인가)")
    void viewerCannotTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(11)), VIEWER_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":3}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — 구직자는 자기 전형 상태를 스스로 바꿀 수 없다 (403)")
    void applicantCannotTransition() throws Exception {
        // 문지후(7)는 application 11 의 당사자이지만, 그렇다고 단계를 옮길 수는 없다.
        mockMvc.perform(asApplicant(patch(STAGE.formatted(11)), APPLICANT_7)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":3}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACCESS_DENIED"));
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — OWNER 도 가능하다")
    void ownerCanTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(1)), OWNER_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"CANCEL","reason":"합격 취소"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.changedByUserId").value(1));
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — 단계 건너뛰기(면접에서 서류접수로 역행)는 409")
    void invalidTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(11)), RECRUITER_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":1}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STAGE_TRANSITION"))
                .andExpect(jsonPath("$.currentStageTypeId").value(2))
                .andExpect(jsonPath("$.requestedStageTypeId").value(1));
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — 최종합격(3) CANCEL 시 직전 단계로 복구")
    void cancelTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(1)), RECRUITER_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"CANCEL","reason":"합격 취소"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fromStageTypeId").value(3))
                .andExpect(jsonPath("$.toStageTypeId").value(2));
    }

    @Test
    @Transactional
    @DisplayName("상태 전이 — 다른 테넌트의 지원 건은 404")
    void crossTenantTransition() throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(1)), RECRUITER_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"CANCEL"}
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    @DisplayName("상태 전이 — 토큰 없이 요청하면 401")
    void transitionWithoutToken() throws Exception {
        mockMvc.perform(patch(STAGE.formatted(11))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":3}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("TOKEN_REQUIRED"));
    }

    // ────────────────── 합격자 필터 두 구현의 동등성 ──────────────────

    /**
     * 전략 두 구현이 같은 데이터에 대해 같은 결과를 내는지 대조한다.
     *
     * <p>JPA 구현은 과제 원본 SQL 을 HQL 로 옮긴 것이다. 옮기는 과정에서 조건이 하나라도 빠지면
     * 제출한 쿼리와 실제 동작이 갈리므로, 더미의 모든 (기업, 공고) 조합에서 순서까지 같아야 한다.
     */
    // 서비스를 거치지 않고 두 Finder 를 직접 부르므로 트랜잭션을 여기서 연다.
    // JPA 구현은 판정 HQL 이 job_posting 을 fetch join 하지 않아(필터 조건에만 쓰인다)
    // DTO 조립 때 프록시를 초기화한다 — 운영에서는 PassedApplicantService 가 여는 트랜잭션 안이다.
    @Test
    @Transactional
    @DisplayName("합격자 필터 — JPA 구현과 원본 SQL 구현의 결과가 완전히 같다")
    void bothFinderStrategiesAgree() {
        assertStrategiesAgree(1, 1);
        assertStrategiesAgree(1, 2);
        assertStrategiesAgree(2, 3);
        assertStrategiesAgree(2, 4);
    }

    @Test
    @Transactional
    @DisplayName("합격자 필터 — 미래 경력이 섞여 있어도 두 구현의 결과가 같다")
    void bothFinderStrategiesAgreeWithFutureDatedCareer() throws Exception {
        // 이 테스트가 지키는 것: 경력 개월수가 음수가 될 수 있는 데이터가 들어와도
        // HQL(부등식, floor 전제)과 원본 SQL(DIV, 0 방향 절단)이 같은 답을 내야 한다.
        // 세 계산 지점(HQL · 네이티브 SQL · Applicant.careerYearsOf)이 같은 규칙을 쓰는지 확인한다.
        // 하나라도 CASE 를 빠뜨리면 여기서 갈린다.
        String email = "future-career@example.com";
        long applicantId = registerApplicantWithFutureCareer(email);
        long jobPostingId = createJobPosting();

        apply(email, jobPostingId);
        long applicationId = latestApplicationIdOf(applicantId, jobPostingId);
        forward(applicationId, 2);
        forward(applicationId, 3);

        assertStrategiesAgree(1, jobPostingId);
    }

    @Test
    @Transactional
    @DisplayName("합격자 필터 — 아직 시작하지 않은 경력은 경력연수를 깎지 않는다")
    void futureCareerDoesNotReduceReportedYears() throws Exception {
        String email = "future-years@example.com";
        long applicantId = registerApplicantWithFutureCareer(email);
        long jobPostingId = createJobPosting();

        apply(email, jobPostingId);
        long applicationId = latestApplicationIdOf(applicantId, jobPostingId);
        forward(applicationId, 2);
        forward(applicationId, 3);

        // 과거 경력(2015~현재)만 세어야 한다. 미래 경력을 그대로 더했다면 개월합이 음수가 되어
        // 요구 연차 3년을 못 넘기고 '목록에서 통째로 사라졌을' 것이다.
        // 그래서 존재 확인이 먼저다 — 빈 배열에서는 everyItem 이 무조건 통과하기 때문이다.
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(jobPostingId)), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.applicantId == %d)]".formatted(applicantId))
                        .isNotEmpty())
                .andExpect(jsonPath("$.items[?(@.applicantId == %d)].careerYears".formatted(applicantId))
                        .value(org.hamcrest.Matchers.everyItem(
                                org.hamcrest.Matchers.greaterThanOrEqualTo(10))));
    }

    /**
     * 과거 경력 + <b>아직 시작하지 않은 경력</b>을 함께 가진 지원자를 만든다.
     *
     * <p>미래 시작일은 입력 검증에서 막지 않는다 — 입사 예정 경력을 미리 등록하는 것은 정상이다.
     * 음수는 계산 규칙이 막는다.
     */
    private long registerApplicantWithFutureCareer(String email) throws Exception {
        String body = """
                {
                  "name": "미래경력 지원자",
                  "email": "%s",
                  "password": "%s",
                  "educations": [
                    {"degreeCode": "BACHELOR", "majorName": "컴퓨터공학", "schoolName": "한국대학교"}
                  ],
                  "careers": [
                    {"positionCode": "BE", "companyName": "이전회사",
                     "startAt": "2015-01-01T00:00:00",
                     "skillCodes": ["JAVA", "SPRINGBOOT", "AWS"]},
                    {"positionCode": "BE", "companyName": "입사예정회사",
                     "startAt": "2099-01-01T00:00:00",
                     "skillCodes": ["JAVA"]}
                  ]
                }
                """.formatted(email, APPLICANT_PASSWORD);

        String response = mockMvc.perform(post(APPLICANTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("applicantId").asLong();
    }

    private void apply(String email, long jobPostingId) throws Exception {
        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/" + jobPostingId + "/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());
    }

    private long latestApplicationIdOf(long applicantId, long jobPostingId) {
        return jdbcTemplate.queryForObject(
                "SELECT application_id FROM application WHERE applicant_id = ? AND job_posting_id = ?",
                Long.class, applicantId, jobPostingId);
    }

    private void assertStrategiesAgree(long companyId, long jobPostingId) {
        LocalDateTime now = LocalDateTime.now();
        List<PassedApplicant> byNativeSql = nativeSqlFinder.find(companyId, jobPostingId, now);
        List<PassedApplicant> byJpa = jpaFinder.find(companyId, jobPostingId, now);

        org.assertj.core.api.Assertions
                .assertThat(byJpa)
                .as("company=%d, jobPosting=%d — JPA(HQL) 결과가 과제 원본 SQL 결과와 달라졌다",
                        companyId, jobPostingId)
                .containsExactlyElementsOf(byNativeSql);
    }

    // ────────────────── 회원가입 / 공고 등록 / 지원 ──────────────────

    /**
     * 이 서비스가 실제로 굴러가는지를 한 번에 보는 테스트.
     *
     * <p>더미 SQL 이 미리 넣어 둔 지원 건에 기대지 않고 <b>API 호출만으로</b>
     * 회원가입 → 로그인 → 공고 열람 → 지원 → 전이 → 합격자 조회까지 전 과정을 만든다. 마지막 단계에서
     * 과제 원본 SQL 이 방금 만든 데이터를 그대로 읽어내면, JPA 로 쓴 결과와 제출 쿼리가 어긋나지
     * 않는다는 뜻이다.
     *
     * <p>주체가 도중에 바뀌는 것에 주목 — 회원가입과 지원은 <b>구직자</b>가, 공고 등록과 전형 진행은
     * <b>담당자</b>가 한다. 한 토큰으로는 이 흐름을 끝까지 갈 수 없다.
     */
    @Test
    @Transactional
    @DisplayName("end-to-end — 회원가입 → 공고 열람 → 지원 → 전이 2회 → 합격자 필터에 등장")
    void endToEndHiringFlow() throws Exception {
        String email = "e2e@example.com";
        long applicantId = registerApplicant(email);
        long jobPostingId = createJobPosting();

        // ① 구직자가 공개 목록에서 공고를 본다 — 지원할 대상을 고르는 경로
        mockMvc.perform(get(PUBLIC_JOB_POSTINGS + "/" + jobPostingId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobPostingId").value(jobPostingId));

        // ② 지원 — 지원 건과 초기 이력이 함께 생긴다. 본문에 지원자 식별자가 없다.
        String applied = mockMvc.perform(
                        asApplicant(post(PUBLIC_JOB_POSTINGS + "/" + jobPostingId + "/applications"), email)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("""
                                        {"reason": "백엔드 10년차입니다. 지원합니다."}
                                        """))
                .andExpect(status().isCreated())
                .andExpect(header().exists(HttpHeaders.LOCATION))
                .andExpect(jsonPath("$.stageCode").value("APPLIED"))
                .andExpect(jsonPath("$.applicantId").value(applicantId))
                // 테넌트는 요청이 아니라 공고 주인에서 온다
                .andExpect(jsonPath("$.companyId").value(1))
                .andExpect(jsonPath("$.createdBy").value(email))
                .andReturn().getResponse().getContentAsString();
        long applicationId = objectMapper.readTree(applied).get("applicationId").asLong();

        // 구직자 본인이 만든 첫 단계이므로 담당자 FK 는 비어 있다.
        Long createdBy = jdbcTemplate.queryForObject(
                "SELECT created_by FROM stage WHERE application_id = ? ORDER BY stage_id ASC LIMIT 1",
                Long.class, applicationId);
        org.assertj.core.api.Assertions.assertThat(createdBy).isNull();

        // 현행(application)과 이력(stage)이 같은 단계로 함께 만들어졌는가 —
        // 이 프로젝트에서 코드가 보장해야 하는 핵심 불변식이다.
        assertCurrentStageMatchesHistory(applicationId, 1);

        // ③ 담당자가 서류접수 → 면접 → 최종합격으로 진행
        forward(applicationId, 2);
        forward(applicationId, 3);
        assertCurrentStageMatchesHistory(applicationId, 3);

        // ④ 과제 SQL(합격자 필터)이 방금 만든 지원자를 읽어낸다
        mockMvc.perform(as(get(PASSED_APPLICANTS.formatted(jobPostingId)), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].applicantId").value(applicantId))
                .andExpect(jsonPath("$.items[0].currentStageCode").value("HIRED"));
    }

    @Test
    @Transactional
    @DisplayName("회원가입 — 토큰 없이 되고, 직무별 경력연수를 합격자 필터와 같은 방식으로 계산해 내려준다")
    void signUpComputesCareerYears() throws Exception {
        mockMvc.perform(post(APPLICANTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicantBody("career-years@example.com")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.educations[0].degreeCode").value("BACHELOR"))
                .andExpect(jsonPath("$.careers[0].employed").value(true))
                .andExpect(jsonPath("$.careerYearsByPosition[0].positionCode").value("BE"))
                // 2015-01-01 부터 재직 중이므로 10년 이상이다(테스트가 오늘 날짜에 매이지 않도록 하한만 본다)
                .andExpect(jsonPath("$.careerYearsByPosition[0].years",
                        org.hamcrest.Matchers.greaterThanOrEqualTo(10)));
    }

    @Test
    @Transactional
    @DisplayName("회원가입 — 방금 만든 계정으로 바로 로그인된다")
    void canLogInRightAfterSignUp() throws Exception {
        String email = "fresh-signup@example.com";
        registerApplicant(email);

        mockMvc.perform(applicantLoginRequest(email, APPLICANT_PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberType").value("APPLICANT"))
                .andExpect(jsonPath("$.accessToken").isNotEmpty());
    }

    @Test
    @Transactional
    @DisplayName("회원가입 — 이메일이 이미 있으면 409 (uq_applicant_email)")
    void duplicateApplicantEmail() throws Exception {
        String existingEmail = jdbcTemplate.queryForObject(
                "SELECT email FROM applicant WHERE applicant_id = 7", String.class);

        mockMvc.perform(post(APPLICANTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicantBody(existingEmail)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_RESOURCE"));
    }

    @Test
    @Transactional
    @DisplayName("회원가입 — 마스터에 없는 스킬 코드는 400 UNKNOWN_MASTER_CODE")
    void unknownSkillCode() throws Exception {
        mockMvc.perform(post(APPLICANTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"테스트","email":"unknown-skill@example.com",
                                 "password":"applicant1234",
                                 "careers":[{"positionCode":"BE","companyName":"회사",
                                             "startAt":"2020-01-01T00:00:00",
                                             "skillCodes":["COBOL"]}]}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("UNKNOWN_MASTER_CODE"));
    }

    @Test
    @Transactional
    @DisplayName("공고 등록 — company_id 는 요청에도 경로에도 없고 토큰에서 온다")
    void createJobPostingUsesTokenTenant() throws Exception {
        mockMvc.perform(as(post(COMPANY_JOB_POSTINGS), RECRUITER_2)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB_POSTING_BODY))
                .andExpect(status().isCreated())
                // 기업2 계정으로 만들었으므로 기업2 소유다. 요청 어디에도 그 정보가 없었다.
                .andExpect(jsonPath("$.companyId").value(2))
                .andExpect(jsonPath("$.requiredSkills.length()").value(2))
                .andExpect(jsonPath("$.open").value(true));
    }

    @Test
    @Transactional
    @DisplayName("지원 — 같은 공고에 두 번 지원하면 409 (3-3 중복 지원 금지)")
    void duplicateApplication() throws Exception {
        String email = "dup@example.com";
        registerApplicant(email);
        long jobPostingId = createJobPosting();
        String url = PUBLIC_JOB_POSTINGS + "/" + jobPostingId + "/applications";

        mockMvc.perform(asApplicant(post(url), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated());

        mockMvc.perform(asApplicant(post(url), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_APPLICATION"));
    }

    @Test
    @Transactional
    @DisplayName("지원 — 회사를 가리지 않는다. 같은 지원자가 기업1·기업2 공고에 모두 지원할 수 있다")
    void applicantCanApplyAcrossCompanies() throws Exception {
        String email = "multi@example.com";
        registerApplicant(email);

        // 기업1 공고 1, 기업2 공고 3 — 둘 다 남의 회사지만 구직자에게는 그게 정상이다.
        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/1/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(1));

        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/3/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyId").value(2));
    }

    @Test
    @Transactional
    @DisplayName("지원 — 마감된 공고에는 지원할 수 없고(409), 마감은 두 번 되지 않는다")
    void cannotApplyToClosedJobPosting() throws Exception {
        String email = "closed@example.com";
        registerApplicant(email);
        long jobPostingId = createJobPosting();

        mockMvc.perform(as(patch(COMPANY_JOB_POSTINGS + "/" + jobPostingId + "/close"), RECRUITER_1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.open").value(false));

        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/" + jobPostingId + "/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_CLOSED"));

        // 이미 마감된 공고를 다시 마감하면 조용히 성공시키지 않는다
        mockMvc.perform(as(patch(COMPANY_JOB_POSTINGS + "/" + jobPostingId + "/close"), RECRUITER_1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("JOB_POSTING_CLOSED"));
    }

    @Test
    @Transactional
    @DisplayName("지원 — 없는 공고는 404")
    void applyToUnknownJobPosting() throws Exception {
        String email = "unknown-posting@example.com";
        registerApplicant(email);

        mockMvc.perform(asApplicant(post(PUBLIC_JOB_POSTINGS + "/999999/applications"), email)
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    // ─────────────────────────── helpers ───────────────────────────

    /** 구직자·비회원 경로. {@code /companies} 접두어가 없다. */
    private static final String APPLICANTS = "/api/v1/applicants";
    private static final String PUBLIC_JOB_POSTINGS = "/api/v1/job-postings";

    /** 기업 담당자 경로. 접두어는 붙지만 company_id 는 담기지 않는다 — 테넌트는 토큰에서 온다. */
    private static final String COMPANY_JOB_POSTINGS = "/api/v1/companies/job-postings";
    private static final String PASSED_APPLICANTS = COMPANY_JOB_POSTINGS + "/%d/passed-applicants";
    private static final String STAGE = "/api/v1/companies/applications/%d/stage";

    /** 이 공고의 요구조건은 {@link #applicantBody} 의 지원자가 정확히 충족하도록 맞춰 뒀다. */
    private static final String JOB_POSTING_BODY = """
            {
              "title": "통합테스트 백엔드 채용",
              "content": "통합테스트용 공고",
              "positionCode": "BE",
              "requiredSkillCodes": ["JAVA", "SPRINGBOOT"],
              "requiredCareers": [{"positionCode": "BE", "years": 3}],
              "requiredEducations": [{"degreeCode": "BACHELOR", "majorName": "컴퓨터공학"}]
            }
            """;

    private static String applicantBody(String email) {
        return """
                {
                  "name": "통합테스트 지원자",
                  "email": "%s",
                  "password": "%s",
                  "birthDate": "1990-05-05",
                  "gender": true,
                  "educations": [
                    {"degreeCode": "BACHELOR", "majorName": "컴퓨터공학", "schoolName": "한국대학교"}
                  ],
                  "careers": [
                    {"positionCode": "BE", "companyName": "이전회사",
                     "startAt": "2015-01-01T00:00:00",
                     "skillCodes": ["JAVA", "SPRINGBOOT", "AWS"]}
                  ]
                }
                """.formatted(email, APPLICANT_PASSWORD);
    }

    /** 회원가입은 비인증이다 — 토큰을 싣지 않는다. */
    private long registerApplicant(String email) throws Exception {
        String body = mockMvc.perform(post(APPLICANTS)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(applicantBody(email)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("applicantId").asLong();
    }

    private long createJobPosting() throws Exception {
        String body = mockMvc.perform(as(post(COMPANY_JOB_POSTINGS), RECRUITER_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(JOB_POSTING_BODY))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(body).get("jobPostingId").asLong();
    }

    /**
     * 모집 기간을 지정해 공고를 만든다.
     *
     * <p>등록 API 는 {@code open_dt}/{@code close_dt} 에 과거·미래 제약을 두지 않는다. 지난 기간의
     * 공고를 만들 수 있다는 점이 여기서는 오히려 필요하다 — 담당자가 마감을 누르지 않은 채
     * 기간만 지난 상태를 그대로 재현할 수 있어야 하기 때문이다.
     *
     * @param closeAt null 이면 마감일 없음(무기한 모집)
     */
    private long createJobPosting(String openAt, String closeAt) throws Exception {
        String body = JOB_POSTING_BODY.strip();
        String period = "\"openAt\": \"%s\", ".formatted(openAt)
                + (closeAt == null ? "" : "\"closeAt\": \"%s\", ".formatted(closeAt));
        body = "{" + period + body.substring(1);

        String response = mockMvc.perform(as(post(COMPANY_JOB_POSTINGS), RECRUITER_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return objectMapper.readTree(response).get("jobPostingId").asLong();
    }

    private void forward(long applicationId, int toStageTypeId) throws Exception {
        mockMvc.perform(as(patch(STAGE.formatted(applicationId)), RECRUITER_1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"transition":"FORWARD","toStageTypeId":%d}
                                """.formatted(toStageTypeId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.toStageTypeId").value(toStageTypeId));
    }

    /** 현행 단계와 최신 이력 단계가 일치하는지 raw SQL 로 대조한다 — JPA 가 쓴 결과를 직접 확인. */
    private void assertCurrentStageMatchesHistory(long applicationId, int expectedStageTypeId) {
        Integer current = jdbcTemplate.queryForObject(
                "SELECT stage_type_id FROM application WHERE application_id = ?",
                Integer.class, applicationId);
        Integer latestHistory = jdbcTemplate.queryForObject(
                "SELECT stage_type_id FROM stage WHERE application_id = ? ORDER BY stage_id DESC LIMIT 1",
                Integer.class, applicationId);

        org.assertj.core.api.Assertions.assertThat(current).isEqualTo(expectedStageTypeId);
        org.assertj.core.api.Assertions.assertThat(latestHistory).isEqualTo(expectedStageTypeId);
    }

    private MockHttpServletRequestBuilder companyLoginRequest(String email, String password) {
        return loginRequest("/api/v1/auth/companies/login", email, password);
    }

    private MockHttpServletRequestBuilder applicantLoginRequest(String email, String password) {
        return loginRequest("/api/v1/auth/login", email, password);
    }

    private MockHttpServletRequestBuilder loginRequest(String url, String email, String password) {
        return post(url)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password));
    }

    /** 기업 담당자로 로그인해 받은 토큰을 Authorization 헤더에 싣는다. */
    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder, String email) throws Exception {
        return withToken(builder, companyLoginRequest(email, PASSWORD));
    }

    /** 구직자로 로그인해 받은 토큰을 Authorization 헤더에 싣는다. */
    private MockHttpServletRequestBuilder asApplicant(MockHttpServletRequestBuilder builder, String email)
            throws Exception {
        return withToken(builder, applicantLoginRequest(email, APPLICANT_PASSWORD));
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder,
                                                    MockHttpServletRequestBuilder login) throws Exception {
        String body = mockMvc.perform(login)
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        JsonNode json = objectMapper.readTree(body);
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + json.get("accessToken").asText());
    }
}
