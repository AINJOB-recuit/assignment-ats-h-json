package com.ainjob.ats.stage;

import com.ainjob.ats.application.ApplicationRepository;
import com.ainjob.ats.auth.CompanyUserRepository;
import com.ainjob.ats.common.ResourceNotFoundException;
import com.ainjob.ats.domain.Application;
import com.ainjob.ats.domain.CompanyUser;
import com.ainjob.ats.domain.Stage;
import com.ainjob.ats.domain.StageType;
import com.ainjob.ats.master.StageTypeRepository;
import com.ainjob.ats.notification.StageChangedEvent;
import com.ainjob.ats.tenant.CrossTenantAccessException;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * ATS 상태 전이 유스케이스.
 *
 * <p>Application + Stage 는 하나의 Aggregate 다. 현재 단계 갱신과 이력 적재를
 * 서비스가 따로 호출하지 않고 {@link Application#moveTo} 한 번으로 처리한다 — 이력 없이 단계만
 * 바뀌는 경로가 존재하지 않는다.
 *
 * <p>동시 전이는 대상 지원 건을 행 잠금으로 읽어 직렬화한다
 * ({@link ApplicationRepository#findByIdForUpdate}).
 */
@Service
public class StageTransitionService {

    private final ApplicationRepository applicationRepository;
    private final StageTypeRepository stageTypeRepository;
    private final CompanyUserRepository companyUserRepository;
    private final StageTransitionPolicy policy;
    private final ApplicationEventPublisher eventPublisher;

    public StageTransitionService(ApplicationRepository applicationRepository,
                                  StageTypeRepository stageTypeRepository,
                                  CompanyUserRepository companyUserRepository,
                                  StageTransitionPolicy policy,
                                  ApplicationEventPublisher eventPublisher) {
        this.applicationRepository = applicationRepository;
        this.stageTypeRepository = stageTypeRepository;
        this.companyUserRepository = companyUserRepository;
        this.policy = policy;
        this.eventPublisher = eventPublisher;
    }

    @Transactional
    public StageTransitionResponse transition(long companyId, long applicationId,
                                              StageTransitionRequest request,
                                              long actorUserId, String actorEmail) {
        Application application = lockForTransition(applicationId);

        // 존재하지만 다른 테넌트 소유 → 403
        if (!application.isOwnedBy(companyId)) {
            throw new CrossTenantAccessException("application", applicationId, companyId);
        }

        StageType current = masterStage(application.getCurrentStage().getId());

        StageType previous = request.transition() == TransitionType.CANCEL
                ? application.findPreviousStage()
                        .map(stage -> masterStage(stage.getId()))
                        .orElse(null)
                : null;

        StageType target = policy.resolveTarget(current, request.transition(), previous);
        policy.verifyRequestedTarget(current, target, request.toStageTypeId());

        LocalDateTime now = LocalDateTime.now();
        // 판정에 쓴 마스터 스냅샷은 준영속이므로, 연관을 걸 때는 영속 상태의 참조를 얻는다.
        StageType targetRef = stageTypeRepository.getReferenceById(target.getId());
        CompanyUser actor = companyUserRepository.getReferenceById(actorUserId);

        Stage stage = application.moveTo(targetRef, request.reason(), actor, now);
        // 응답에 stage_id 를 담아야 하므로 생성키를 이 시점에 확정한다.
        applicationRepository.flush();

        OffsetDateTime changedAt = now.atZone(ZoneId.systemDefault()).toOffsetDateTime();

        // 커밋 이후 비동기로 메일 발송 (StageChangedEmailListener)
        eventPublisher.publishEvent(new StageChangedEvent(
                applicationId,
                companyId,
                application.getCompany().getName(),
                application.getJobPosting().getTitle(),
                application.getApplicant().getName(),
                application.getApplicant().getEmail(),
                current.getName(),
                target.getName(),
                request.reason(),
                actorEmail,
                changedAt));

        return new StageTransitionResponse(
                applicationId,
                current.getId(), current.getCode(),
                target.getId(), target.getCode(),
                request.transition(),
                stage.getId(),
                actorUserId,
                actorEmail,
                changedAt,
                true);
    }

    /**
     * 전이 대상을 행 잠금과 함께 읽는다.
     *
     * <p>잠금을 얻지 못하는 상황(같은 지원 건에 대한 동시 전이)은 클라이언트가 재시도하면 되는
     * 일시적 충돌이므로 409 로 알린다.
     */
    private Application lockForTransition(long applicationId) {
        try {
            return applicationRepository.findByIdForUpdate(applicationId)
                    .orElseThrow(() -> new ResourceNotFoundException("application", applicationId));
        } catch (PessimisticLockingFailureException e) {
            throw new StageConcurrentModificationException(applicationId);
        }
    }

    private StageType masterStage(short stageTypeId) {
        return policy.findById(stageTypeId)
                .orElseThrow(() -> new IllegalStateException(
                        "stage_type 마스터에 없는 단계입니다. stage_type_id=" + stageTypeId));
    }
}
