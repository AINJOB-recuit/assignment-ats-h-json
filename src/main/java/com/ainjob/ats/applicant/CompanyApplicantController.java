package com.ainjob.ats.applicant;

import com.ainjob.ats.tenant.TenantContext;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기업 담당자의 지원자 열람 API.
 *
 * <pre>
 * GET /api/v1/companies/job-postings/{jobPostingId}/applicants/{applicantId}
 * Authorization: Bearer {accessToken}
 * </pre>
 *
 * <p>경로가 <b>지원자 단독이 아니라 공고 하위</b>인 것이 요점이다. 지원자는 글로벌 풀이라
 * 그 자체로는 테넌트가 없고, "우리 회사 공고에 지원했다"는 사실만이 열람 근거가 된다.
 * 경로 구조가 그 근거를 그대로 드러낸다.
 *
 * <p>{@code company_id} 는 경로에 없다 — 접두어가 {@code /companies/{id}} 가 아니라
 * {@code /companies} 인 이유다. 테넌트는 토큰에서만 나온다.
 */
@RestController
@RequestMapping("/api/v1/companies/job-postings")
public class CompanyApplicantController {

    private final CompanyApplicantService companyApplicantService;

    public CompanyApplicantController(CompanyApplicantService companyApplicantService) {
        this.companyApplicantService = companyApplicantService;
    }

    @GetMapping("/{jobPostingId}/applicants/{applicantId}")
    public ResponseEntity<ApplicantResponse> findApplicant(@PathVariable long jobPostingId,
                                                           @PathVariable long applicantId) {
        return ResponseEntity.ok(companyApplicantService.findApplicantOfJobPosting(
                TenantContext.companyId(), jobPostingId, applicantId, LocalDateTime.now()));
    }
}
