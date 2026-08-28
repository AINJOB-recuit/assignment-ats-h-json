package com.ainjob.ats.stage;

import java.time.OffsetDateTime;

/**
 * 상태 전이 응답.
 *
 * @param changedBy       처리자 이메일
 * @param changedByUserId stage.created_by 에 기록된 FK 값
 */
public record StageTransitionResponse(
        long applicationId,
        short fromStageTypeId,
        String fromStageCode,
        short toStageTypeId,
        String toStageCode,
        TransitionType transition,
        long stageId,
        long changedByUserId,
        String changedBy,
        OffsetDateTime changedAt,
        boolean notificationRequested) {
}
