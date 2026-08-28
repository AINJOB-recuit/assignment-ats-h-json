package com.ainjob.ats.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.ainjob.ats.domain.Application;
import com.ainjob.ats.domain.JobPosting;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 격리 ② — Hibernate 테넌트 필터가 실제로 걸리는지, 그리고 <b>걸리면 안 되는 곳에 걸리지 않는지</b>.
 *
 * <p>이 테스트가 존재하는 이유는 필터가 <b>암묵적</b>이기 때문이다. 조건이 SQL 에만 나타나고 자바
 * 코드에는 보이지 않으므로, "켜졌다고 믿었는데 안 켜졌다"와 "꺼졌다고 믿었는데 켜졌다"가 둘 다
 * 조용히 일어날 수 있다. 앞의 것은 정보 유출이고, 뒤의 것은 공개 게시판이 텅 비는 장애다.
 */
@SpringBootTest
@AutoConfigureMockMvc
class TenantFilterIntegrationTest {

    private static final String PASSWORD = "ainjob1234!";
    private static final String APPLICANT_PASSWORD = "applicant1234";
    private static final String RECRUITER_1 = "recruiter@company1.com";
    private static final String APPLICANT_7 = "h.json248@gmail.com";

    /** 공고 3 · 지원 건 11 은 기업2 소유다. 기업1 토큰에게는 보이지 않아야 한다. */
    private static final long COMPANY_2_JOB_POSTING = 3L;
    private static final long COMPANY_2_APPLICATION = 11L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @PersistenceContext
    private EntityManager entityManager;

    @AfterEach
    void clearScope() {
        TenantScope.clear();
    }

    // ─────────────── 켜져야 하는 곳: 기업 영역 ───────────────

    @Nested
    @DisplayName("기업 영역 (/api/v1/companies/**) — 필터가 켜진다")
    class CompanyArea {

        @Test
        @DisplayName("남의 회사 공고는 403 이 아니라 404 — 존재 여부조차 알려 주지 않는다")
        void otherTenantJobPostingIsNotFound() throws Exception {
            mockMvc.perform(as(get("/api/v1/companies/job-postings/" + COMPANY_2_JOB_POSTING)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        }

        @Test
        @DisplayName("남의 회사 공고의 합격자 필터도 404")
        void otherTenantPassedApplicantsIsNotFound() throws Exception {
            mockMvc.perform(as(get("/api/v1/companies/job-postings/"
                    + COMPANY_2_JOB_POSTING + "/passed-applicants")))
                    .andExpect(status().isNotFound());
        }

        @Test
        @DisplayName("남의 회사 지원 건 상태 전이도 404 (findByIdForUpdate 는 JPQL 이라 필터를 탄다)")
        void otherTenantStageTransitionIsNotFound() throws Exception {
            mockMvc.perform(as(patch("/api/v1/companies/applications/"
                    + COMPANY_2_APPLICATION + "/stage"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"transition\":\"FORWARD\"}"))
                    .andExpect(status().isNotFound());
        }

        /**
         * 두 응답에서 <b>요청한 식별자만</b> 다르고 나머지는 한 글자도 다르지 않아야 한다.
         * 식별자는 클라이언트가 직접 보낸 값이라 되돌려줘도 새로 알려 주는 정보가 없다.
         * 반대로 코드·메시지·필드 구성이 조금이라도 갈리면 그 차이가 곧 "이 번호는 실재한다"는 신호다.
         */
        @Test
        @DisplayName("존재하지 않는 공고와 남의 회사 공고의 응답이 식별자를 빼면 동일하다")
        void missingAndForeignAreIndistinguishable() throws Exception {
            long missingId = 999999L;
            String missing = mockMvc.perform(as(get("/api/v1/companies/job-postings/" + missingId)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();
            String foreign = mockMvc.perform(as(get("/api/v1/companies/job-postings/"
                    + COMPANY_2_JOB_POSTING)))
                    .andExpect(status().isNotFound())
                    .andReturn().getResponse().getContentAsString();

            assertThat(normalized(foreign, COMPANY_2_JOB_POSTING))
                    .isEqualTo(normalized(missing, missingId));
        }

        @Test
        @DisplayName("내 회사 공고는 정상 조회된다 — 필터가 과하게 걸리지 않는다")
        void ownJobPostingIsVisible() throws Exception {
            mockMvc.perform(as(get("/api/v1/companies/job-postings/1")))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobPostingId").value(1));
        }
    }

    // ─────────────── 꺼져야 하는 곳: 공개 · 구직자 ───────────────

    @Nested
    @DisplayName("공개·구직자 영역 — 필터가 꺼져 있다")
    class PublicArea {

        /**
         * 이 테스트가 이 파일의 존재 이유다.
         *
         * <p>필터를 "기업 회원 토큰이면 켠다"로 구현하면 여기가 깨진다 — 기업 담당자가 로그인한
         * 채로 공개 공고 목록을 보면 자기 회사 공고만 나온다. 그래서 활성화 기준을 토큰이 아니라
         * <b>경로</b>로 잡았고, 그 결정을 이 테스트가 고정한다.
         */
        @Test
        @DisplayName("기업 토큰으로 공개 목록을 봐도 다른 회사 공고가 그대로 보인다")
        void companyTokenStillSeesAllPublicPostings() throws Exception {
            String body = mockMvc.perform(as(get("/api/v1/job-postings")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(companyIdsIn(body))
                    .as("공개 목록은 회사를 가리지 않는다")
                    .contains(COMPANY_2_JOB_POSTING);
        }

        @Test
        @DisplayName("익명 요청의 공개 목록과 기업 토큰의 공개 목록이 같다")
        void anonymousAndCompanyTokenSeeTheSameList() throws Exception {
            String anonymous = mockMvc.perform(get("/api/v1/job-postings"))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            String withCompanyToken = mockMvc.perform(as(get("/api/v1/job-postings")))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();

            assertThat(withCompanyToken).isEqualTo(anonymous);
        }

        @Test
        @DisplayName("기업 토큰으로 남의 회사 공고 상세(공개)를 봐도 200")
        void companyTokenCanReadForeignPostingThroughPublicPath() throws Exception {
            mockMvc.perform(as(get("/api/v1/job-postings/" + COMPANY_2_JOB_POSTING)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.jobPostingId").value(COMPANY_2_JOB_POSTING));
        }

        @Test
        @DisplayName("구직자는 남의 회사가 아니라 '아무 회사' 공고에 지원할 수 있다")
        void applicantCanApplyToAnyCompany() throws Exception {
            // 이미 지원한 건이라 409 다. 중요한 것은 404(공고가 안 보임)가 아니라는 점 —
            // 지원 접수 경로에서 공고 조회에 테넌트 필터가 걸리지 않았다는 뜻이다.
            mockMvc.perform(asApplicant(post("/api/v1/job-postings/"
                    + COMPANY_2_JOB_POSTING + "/applications"))
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{}"))
                    .andExpect(status().isConflict());
        }
    }

    // ─────────────── 필터 자체의 동작 ───────────────

    @Nested
    @DisplayName("필터 동작 — SQL 수준")
    class FilterMechanics {

        @Test
        @Transactional
        @DisplayName("스코프가 없으면 JPQL 이 전체 테넌트를 반환한다")
        void withoutScopeAllTenantsAreVisible() {
            List<JobPosting> all = entityManager
                    .createQuery("select j from JobPosting j", JobPosting.class).getResultList();
            assertThat(all).extracting(JobPosting::getCompanyId).contains(1L, 2L);
        }

        @Test
        @Transactional
        @DisplayName("PK 직접 조회(findById)는 필터를 타지 않는다 — 기업 영역이 JPQL 을 쓰는 이유")
        void findByIdBypassesFilterOnPurpose() {
            enableFilterForCompany1();

            JobPosting foreignByPk = entityManager.find(JobPosting.class, COMPANY_2_JOB_POSTING);
            List<JobPosting> foreignByJpql = entityManager
                    .createQuery("select j from JobPosting j where j.id = :id", JobPosting.class)
                    .setParameter("id", COMPANY_2_JOB_POSTING).getResultList();

            assertThat(foreignByPk)
                    .as("PK 조회는 필터가 적용되지 않는다(Hibernate 동작). 그래서 서비스가 findById 를 쓰면 안 된다")
                    .isNotNull();
            assertThat(foreignByJpql)
                    .as("같은 행을 JPQL 로 읽으면 필터가 걸러 낸다")
                    .isEmpty();
        }

        @Test
        @Transactional
        @DisplayName("Application 에도 같은 필터가 걸린다")
        void applicationIsFilteredToo() {
            enableFilterForCompany1();

            List<Application> applications = entityManager
                    .createQuery("select a from Application a", Application.class).getResultList();

            assertThat(applications).isNotEmpty();
            assertThat(applications).allSatisfy(a ->
                    assertThat(a.getCompanyId()).isEqualTo(1L));
        }

        private void enableFilterForCompany1() {
            entityManager.unwrap(org.hibernate.Session.class)
                    .enableFilter(TenantFilters.TENANT_FILTER)
                    .setParameter(TenantFilters.COMPANY_ID_PARAM, 1L);
        }
    }

    // ─────────────── 헬퍼 ───────────────

    /**
     * 두 오류 응답을 비교 가능한 형태로 만든다 — 요청한 식별자를 자리표시자로 바꾼다.
     * 그 값은 클라이언트가 보낸 것이므로 응답에 실려도 정보가 되지 않는다.
     */
    private String normalized(String body, long requestedId) {
        return body.replace(String.valueOf(requestedId), "{id}");
    }

    private List<Long> companyIdsIn(String listBody) throws Exception {
        return objectMapper.readTree(listBody).findValues("jobPostingId").stream()
                .map(JsonNode::asLong)
                .toList();
    }

    private MockHttpServletRequestBuilder as(MockHttpServletRequestBuilder builder) throws Exception {
        return withToken(builder, "/api/v1/auth/companies/login", RECRUITER_1, PASSWORD);
    }

    private MockHttpServletRequestBuilder asApplicant(MockHttpServletRequestBuilder builder) throws Exception {
        return withToken(builder, "/api/v1/auth/login", APPLICANT_7, APPLICANT_PASSWORD);
    }

    private MockHttpServletRequestBuilder withToken(MockHttpServletRequestBuilder builder,
                                                    String loginUrl, String email, String password)
            throws Exception {
        String body = mockMvc.perform(post(loginUrl)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = objectMapper.readTree(body).get("accessToken").asText();
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
}
