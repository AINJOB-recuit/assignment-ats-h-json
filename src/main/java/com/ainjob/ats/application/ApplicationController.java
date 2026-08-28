package com.ainjob.ats.application;

import com.ainjob.ats.auth.ApplicantContext;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 지원 API — 구직자 본인만 호출한다.
 *
 * <pre>
 * POST /api/v1/job-postings/{jobPostingId}/applications
 * Authorization: Bearer {구직자 accessToken}
 * { "reason": "..." }
 * </pre>
 *
 * <p>공고 하위 경로에 둔 이유: 지원은 항상 특정 공고에 대한 행위이고, 그 공고의 소유 기업이
 * 곧 이 지원 건의 테넌트가 된다.
 *
 * <p><b>지원자 식별자를 어디에서도 받지 않는다.</b> 경로에도 본문에도 없고 토큰에서만 나온다.
 * 그래서 "남의 이름으로 지원하기"는 막히는 것이 아니라 <b>표현할 수 없다</b>.
 */
@RestController
@RequestMapping("/api/v1/job-postings")
public class ApplicationController {

    private final ApplicationService applicationService;

    public ApplicationController(ApplicationService applicationService) {
        this.applicationService = applicationService;
    }

    @PostMapping("/{jobPostingId}/applications")
    public ResponseEntity<ApplicationCreatedResponse> apply(
            @PathVariable long jobPostingId,
            @Valid @RequestBody CreateApplicationRequest request) {

        ApplicationCreatedResponse created = applicationService.apply(
                jobPostingId,
                ApplicantContext.applicantId(),
                request,
                ApplicantContext.email(),
                LocalDateTime.now());

        return ResponseEntity
                .created(URI.create("/api/v1/applications/" + created.applicationId()))
                .body(created);
    }
}
