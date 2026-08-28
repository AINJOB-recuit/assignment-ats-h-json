package com.ainjob.ats.applicant;

import com.ainjob.ats.auth.ApplicantContext;
import jakarta.validation.Valid;
import java.net.URI;
import java.time.LocalDateTime;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 구직자 본인 API.
 *
 * <pre>
 * POST /api/v1/applicants        회원가입 (비인증)
 * GET  /api/v1/applicants/{id}   본인 프로필 + 학력 + 경력 + 직무별 경력연수 (본인만)
 * </pre>
 *
 * <p>경로에 {@code /companies} 가 없다 = 기업 쪽 리소스가 아니다. 지원자는 글로벌 풀이라 응답에
 * {@code company_id} 도 없다.
 *
 * <p>기업 담당자가 지원자를 보는 경로는 {@link CompanyApplicantController} 로 따로 있다. 같은
 * 리소스를 두 경로로 나눈 이유는 <b>볼 수 있는 범위가 다르기 때문</b>이다 — 구직자는 본인만,
 * 기업은 자기 공고에 지원한 사람만.
 */
@RestController
@RequestMapping("/api/v1/applicants")
public class ApplicantController {

    private final ApplicantService applicantService;

    public ApplicantController(ApplicantService applicantService) {
        this.applicantService = applicantService;
    }

    /** 회원가입. 가입 전에는 토큰이 있을 수 없으므로 비인증이다. */
    @PostMapping
    public ResponseEntity<ApplicantResponse> register(@Valid @RequestBody CreateApplicantRequest request) {
        ApplicantResponse created = applicantService.register(request, LocalDateTime.now());
        return ResponseEntity
                .created(URI.create("/api/v1/applicants/" + created.applicantId()))
                .body(created);
    }

    /**
     * 본인 프로필 조회.
     *
     * <p>경로의 식별자는 신뢰하지 않는다 — 토큰 subject 와 대조해 다르면 403 이다. 경로 값을 그대로
     * 쓰면 번호만 바꿔 남의 이력서를 열람할 수 있다.
     */
    @GetMapping("/{applicantId}")
    public ResponseEntity<ApplicantResponse> findOwnProfile(@PathVariable long applicantId) {
        return ResponseEntity.ok(applicantService.findOwnProfile(
                applicantId, ApplicantContext.applicantId(), LocalDateTime.now()));
    }
}
