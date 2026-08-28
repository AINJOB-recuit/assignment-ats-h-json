package com.ainjob.ats.jobposting;

import com.ainjob.ats.tenant.TenantContext;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 기업 채용공고 API — 담당자용.
 *
 * <pre>
 * POST   /api/v1/companies/job-postings              공고 등록 (OWNER / RECRUITER)
 * GET    /api/v1/companies/job-postings              내 회사 공고 목록 (?open=true|false)
 * GET    /api/v1/companies/job-postings/{id}         공고 상세 + 요구조건 (마감 포함)
 * PATCH  /api/v1/companies/job-postings/{id}/close   공고 마감 (OWNER / RECRUITER)
 * </pre>
 *
 * <p><b>경로에 company_id 가 없다.</b> {@code /companies/{id}} 가 아니라 {@code /companies} 인 것이
 * 요점이다 — 접두어는 "토큰의 회사로 스코프된 리소스"라는 표시일 뿐이고, 그 회사가 어디인지는
 * {@link TenantContext} 가 검증된 토큰에서만 읽는다. 남의 회사를 지정할 자리가 URL 문법에 없다.
 *
 * <p>역할 인가는 {@code SecurityConfig} 한 곳에 있다.
 */
@RestController
@RequestMapping("/api/v1/companies/job-postings")
public class CompanyJobPostingController {

    private final JobPostingService jobPostingService;

    public CompanyJobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }

    @PostMapping
    public ResponseEntity<JobPostingResponse> create(@Valid @RequestBody CreateJobPostingRequest request) {
        JobPostingResponse created =
                jobPostingService.create(TenantContext.companyId(), request, LocalDateTime.now());
        return ResponseEntity
                .created(URI.create("/api/v1/companies/job-postings/" + created.jobPostingId()))
                .body(created);
    }

    @GetMapping
    public ResponseEntity<List<JobPostingSummaryResponse>> findMine(
            @RequestParam(name = "open", required = false) Boolean open) {

        return ResponseEntity.ok(
                jobPostingService.findMine(TenantContext.companyId(), open, LocalDateTime.now()));
    }

    @GetMapping("/{jobPostingId}")
    public ResponseEntity<JobPostingResponse> findOne(@PathVariable long jobPostingId) {
        return ResponseEntity.ok(
                jobPostingService.findOne(TenantContext.companyId(), jobPostingId, LocalDateTime.now()));
    }

    @PatchMapping("/{jobPostingId}/close")
    public ResponseEntity<JobPostingResponse> close(@PathVariable long jobPostingId) {
        return ResponseEntity.ok(
                jobPostingService.close(TenantContext.companyId(), jobPostingId, LocalDateTime.now()));
    }
}
