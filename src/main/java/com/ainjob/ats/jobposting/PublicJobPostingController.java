package com.ainjob.ats.jobposting;

import java.time.LocalDateTime;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공개 채용공고 API — 구직자·비회원용.
 *
 * <pre>
 * GET /api/v1/job-postings         모집 중인 전체 공고 (회사 무관)
 * GET /api/v1/job-postings/{id}    공고 상세 + 요구조건 (마감이면 404)
 * </pre>
 *
 * <p>경로에 {@code /companies} 가 없다 = 특정 기업의 리소스가 아니라 공개물이다. 토큰도 필요 없다.
 * 채용공고는 원래 공개되는 것이고, 볼 수 없으면 지원할 공고를 고를 수 없다.
 *
 * <p>같은 공고를 기업이 보는 경로는 {@link CompanyJobPostingController} 다. 그쪽은 자기 회사 것만
 * 보이는 대신 <b>마감된 공고도</b> 보인다 — 담당자에게는 지난 공고도 관리 대상이기 때문이다.
 */
@RestController
@RequestMapping("/api/v1/job-postings")
public class PublicJobPostingController {

    private final JobPostingService jobPostingService;

    public PublicJobPostingController(JobPostingService jobPostingService) {
        this.jobPostingService = jobPostingService;
    }

    @GetMapping
    public ResponseEntity<List<JobPostingSummaryResponse>> findAllOpen() {
        return ResponseEntity.ok(jobPostingService.findAllOpen(LocalDateTime.now()));
    }

    @GetMapping("/{jobPostingId}")
    public ResponseEntity<JobPostingResponse> findOne(@PathVariable long jobPostingId) {
        return ResponseEntity.ok(jobPostingService.findOpenOne(jobPostingId, LocalDateTime.now()));
    }
}
