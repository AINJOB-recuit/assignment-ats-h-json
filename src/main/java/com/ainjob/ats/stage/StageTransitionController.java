package com.ainjob.ats.stage;

import com.ainjob.ats.tenant.TenantContext;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 상태 전이 API. 알림(요구사항 3)의 트리거다.
 *
 * <pre>
 * PATCH /api/v1/companies/applications/{applicationId}/stage
 * </pre>
 *
 * <p>인가는 두 겹이다 — 회원 구분({@code ROLE_COMPANY_USER})으로 구직자를 먼저 걸러 내고,
 * 그다음 역할로 OWNER / RECRUITER 만 통과시킨다(VIEWER 는 403). 규칙은 {@code SecurityConfig}
 * 한 곳에 있다. 테넌트와 처리자는 모두 검증된 토큰에서 나오므로 위조할 수 없다.
 *
 * <p>합격·불합격을 결정하는 것은 기업이므로 이 경로는 {@code /companies} 하위다. 구직자는
 * 자기 전형 상태를 바꿀 수 없고, 변경 결과를 이메일로 통보받는다.
 */
@RestController
@RequestMapping("/api/v1/companies/applications")
public class StageTransitionController {

    private final StageTransitionService stageTransitionService;

    public StageTransitionController(StageTransitionService stageTransitionService) {
        this.stageTransitionService = stageTransitionService;
    }

    @PatchMapping("/{applicationId}/stage")
    public ResponseEntity<StageTransitionResponse> transition(
            @PathVariable long applicationId,
            @Valid @RequestBody StageTransitionRequest request) {

        return ResponseEntity.ok(stageTransitionService.transition(
                TenantContext.companyId(),
                applicationId,
                request,
                TenantContext.companyUserId(),
                TenantContext.email()));
    }
}
