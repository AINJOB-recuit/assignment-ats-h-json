package com.ainjob.ats.applicant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Company;
import com.ainjob.ats.domain.JobPosting;
import com.ainjob.ats.jobposting.JobPostingRepository;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 합격자 필터 전략 선택.
 *
 * <p>두 구현이 모두 빈으로 등록돼 있어도 <b>설정한 전략 하나만</b> 호출돼야 한다.
 * 실제 필터 결과가 같은지는 실 DB 통합 테스트(두 구현 동등성)가 검증한다.
 */
class PassedApplicantServiceTest {

    private static final long COMPANY_ID = 1L;
    private static final long JOB_POSTING_ID = 1L;

    private PassedApplicantFinder jpaFinder;
    private PassedApplicantFinder nativeSqlFinder;
    private JobPostingRepository jobPostingRepository;

    @BeforeEach
    void setUp() {
        jpaFinder = mock(PassedApplicantFinder.class);
        given(jpaFinder.strategy()).willReturn(PassedApplicantStrategy.JPA);

        nativeSqlFinder = mock(PassedApplicantFinder.class);
        given(nativeSqlFinder.strategy()).willReturn(PassedApplicantStrategy.NATIVE_SQL);

        // 스터빙 표현식 안에서 다른 목을 만들면 Mockito 가 스터빙이 끝나지 않은 것으로 본다.
        // 공고 목을 먼저 완성한 뒤 저장소를 스터빙한다.
        JobPosting jobPosting = ownedJobPosting();
        jobPostingRepository = mock(JobPostingRepository.class);
        given(jobPostingRepository.findByIdInTenantScope(JOB_POSTING_ID)).willReturn(Optional.of(jobPosting));
    }

    @Test
    @DisplayName("기본 전략은 JPA다 — 설정을 비워도 JPA 구현이 호출된다")
    void defaultsToJpa() {
        PassedApplicantService service = service(new PassedApplicantProperties(null));

        assertThat(service.activeStrategy()).isEqualTo(PassedApplicantStrategy.JPA);

        service.findPassedApplicants(COMPANY_ID, JOB_POSTING_ID);

        verify(jpaFinder).find(eq(COMPANY_ID), eq(JOB_POSTING_ID), any(LocalDateTime.class));
        verify(nativeSqlFinder, never()).find(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("NATIVE_SQL로 설정하면 과제 원본 SQL 구현만 호출된다")
    void usesNativeSqlWhenConfigured() {
        PassedApplicantService service =
                service(new PassedApplicantProperties(PassedApplicantStrategy.NATIVE_SQL));

        service.findPassedApplicants(COMPANY_ID, JOB_POSTING_ID);

        verify(nativeSqlFinder).find(eq(COMPANY_ID), eq(JOB_POSTING_ID), any(LocalDateTime.class));
        verify(jpaFinder, never()).find(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("설정한 전략의 구현이 없으면 조회 때가 아니라 기동 때 실패한다")
    void failsFastWhenStrategyHasNoImplementation() {
        assertThatThrownBy(() -> new PassedApplicantService(
                List.of(jpaFinder),
                jobPostingRepository,
                new PassedApplicantProperties(PassedApplicantStrategy.NATIVE_SQL)))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("NATIVE_SQL");
    }

    @Test
    @DisplayName("2차 방어 — 테넌트 필터가 못 걸러 낸 남의 공고도 isOwnedBy 가 막는다")
    void tenantCheckRunsBeforeFilter() {
        PassedApplicantService service = service(new PassedApplicantProperties(null));

        assertThatThrownBy(() -> service.findPassedApplicants(999L, JOB_POSTING_ID))
                .isInstanceOf(CrossTenantAccessException.class);

        verify(jpaFinder, never()).find(anyLong(), anyLong(), any());
    }

    @Test
    @DisplayName("전략과 무관하게 없는 공고는 404 — 필터를 돌리지 않는다")
    void unknownJobPostingIsNotFound() {
        given(jobPostingRepository.findByIdInTenantScope(404L)).willReturn(Optional.empty());
        PassedApplicantService service = service(new PassedApplicantProperties(null));

        assertThatThrownBy(() -> service.findPassedApplicants(COMPANY_ID, 404L))
                .isInstanceOf(ResourceNotFoundException.class);

        verify(jpaFinder, never()).find(anyLong(), anyLong(), any());
    }

    private PassedApplicantService service(PassedApplicantProperties properties) {
        return new PassedApplicantService(
                List.of(jpaFinder, nativeSqlFinder), jobPostingRepository, properties);
    }

    /** 소유 테넌트가 COMPANY_ID 인 공고. 응답 헤더(회사명·공고명)도 여기서 나온다. */
    private static JobPosting ownedJobPosting() {
        Company company = mock(Company.class);
        given(company.getId()).willReturn(COMPANY_ID);
        given(company.getName()).willReturn("기업1");

        JobPosting jobPosting = mock(JobPosting.class);
        given(jobPosting.isOwnedBy(COMPANY_ID)).willReturn(true);
        given(jobPosting.getCompany()).willReturn(company);
        given(jobPosting.getTitle()).willReturn("기업1 백엔드 개발자 채용");
        return jobPosting;
    }
}
