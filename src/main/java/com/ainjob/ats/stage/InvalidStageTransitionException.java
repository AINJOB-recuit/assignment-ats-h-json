package com.ainjob.ats.stage;

/**
 * 상태 전이 규칙 위반 → 409 Conflict.
 *
 * <p>409를 택한 이유: 요청 자체는 문법적으로 유효하고, 리소스의 "현재 단계"와 충돌하기 때문이다.
 * 같은 요청이라도 현재 단계가 바뀌면 성공할 수 있으므로 422(처리 불가)보다 409가 적합하다.
 */
public class InvalidStageTransitionException extends RuntimeException {

    private final Short currentStageTypeId;
    private final Short requestedStageTypeId;

    public InvalidStageTransitionException(String message, Short currentStageTypeId, Short requestedStageTypeId) {
        super(message);
        this.currentStageTypeId = currentStageTypeId;
        this.requestedStageTypeId = requestedStageTypeId;
    }

    public Short getCurrentStageTypeId() {
        return currentStageTypeId;
    }

    public Short getRequestedStageTypeId() {
        return requestedStageTypeId;
    }
}
