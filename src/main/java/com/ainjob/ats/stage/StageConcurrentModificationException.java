package com.ainjob.ats.stage;

/**
 * 낙관적 잠금 실패 → 409 Conflict.
 *
 * <p>UPDATE 시 "읽은 시점의 단계"를 WHERE 조건에 포함한다. 두 담당자가 동시에 같은 지원의
 * 단계를 옮기면 뒤늦은 쪽이 0행 갱신이 되어 여기로 떨어진다(잃어버린 갱신 방지).
 */
public class StageConcurrentModificationException extends RuntimeException {

    public StageConcurrentModificationException(long applicationId) {
        super("다른 요청이 먼저 단계를 변경했습니다. 다시 조회 후 시도하세요. applicationId=" + applicationId);
    }
}
