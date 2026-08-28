package com.ainjob.ats.common;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 공통 오류 응답 본문.
 *
 * <p>상태 전이 충돌(409)은 현재/요청 단계 PK를 함께 내려준다.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiErrorResponse(
        String code,
        String message,
        Short currentStageTypeId,
        Short requestedStageTypeId,
        String path) {

    public static ApiErrorResponse of(ErrorCode code, String message, String path) {
        return new ApiErrorResponse(code.name(), message, null, null, path);
    }

    public static ApiErrorResponse ofStageConflict(ErrorCode code, String message,
                                                   Short currentStageTypeId,
                                                   Short requestedStageTypeId,
                                                   String path) {
        return new ApiErrorResponse(code.name(), message, currentStageTypeId, requestedStageTypeId, path);
    }
}
