package com.ainjob.ats.stage;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * 상태 전이 요청.
 *
 * @param transition      FORWARD / REJECT / CANCEL
 * @param toStageTypeId   목표 단계 PK. CANCEL은 서버가 stage 이력에서 역산하므로 생략 가능하며,
 *                        값을 보내면 서버 계산 결과와 일치하는지 검증한다(불일치 시 409).
 * @param reason          stage.content 에 기록할 사유
 */
public record StageTransitionRequest(
        @NotNull(message = "transition은 필수입니다. (FORWARD / REJECT / CANCEL)")
        TransitionType transition,

        @Positive(message = "toStageTypeId는 1 이상이어야 합니다.")
        Short toStageTypeId,

        @Size(max = 1000, message = "reason은 1000자 이하여야 합니다.")
        String reason) {
}
