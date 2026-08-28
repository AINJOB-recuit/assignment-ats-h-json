package com.ainjob.ats.applicant;

import com.ainjob.ats.tenant.TenantContext;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 요구사항 1 — ATS 합격자 필터 API.
 *
 * <pre>
 * GET /api/v1/companies/job-postings/{jobPostingId}/passed-applicants
 * Authorization: Bearer {기업 담당자 accessToken}
 * </pre>
 *
 * <p>company_id는 요청 어디에서도 받지 않는다. {@code /companies} 접두어는 "토큰의 회사로 스코프된
 * 리소스"라는 표시일 뿐 식별자를 담지 않으며, 실제 기업은 로그인한 계정의 토큰 클레임에서만 나온다.
 * 다른 회사의 합격자를 조회할 방법 자체가 없다.
 *
 * <p>구직자 토큰으로는 이 경로에 도달하지 못한다 — {@code SecurityConfig} 가 회원 구분
 * ({@code ROLE_COMPANY_USER})부터 확인한다.
 */
@RestController
@RequestMapping("/api/v1/companies/job-postings")
public class PassedApplicantController {

    private final PassedApplicantService passedApplicantService;

    public PassedApplicantController(PassedApplicantService passedApplicantService) {
        this.passedApplicantService = passedApplicantService;
    }

    @GetMapping("/{jobPostingId}/passed-applicants")
    public ResponseEntity<PassedApplicantsResponse> findPassedApplicants(@PathVariable long jobPostingId) {
        long companyId = TenantContext.companyId();
        return ResponseEntity.ok(passedApplicantService.findPassedApplicants(companyId, jobPostingId));
    }
}
