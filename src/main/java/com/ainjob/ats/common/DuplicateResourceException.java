package com.ainjob.ats.common;

/**
 * 유일성 제약을 위반한 등록 요청 → 409.
 *
 * <p>DB 의 UNIQUE 제약이 최종 방어선이고, 이 예외는 그 전에 미리 걸러 원인을 분명히 알려주기 위한
 * 것이다. 동시 요청으로 선조회를 통과한 경우에는 DB 가 거부하고
 * {@code GlobalExceptionHandler} 가 같은 409 로 변환한다.
 */
public class DuplicateResourceException extends RuntimeException {

    public DuplicateResourceException(String message) {
        super(message);
    }
}
