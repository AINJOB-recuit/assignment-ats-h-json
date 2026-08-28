package com.ainjob.ats.applicant;

/**
 * 구직자가 본인이 아닌 지원자의 프로필을 조회하려 했을 때 → 403.
 *
 * <p>존재 여부를 먼저 확인하지 않고 <b>본인 여부부터</b> 판정한다. 순서를 뒤집으면 404/403 차이로
 * "그 번호의 지원자가 실제로 있는지"를 알아낼 수 있게 된다 — 지원자 식별자를 1부터 훑어
 * 가입자 수를 세는 것이 가능해진다.
 */
public class NotOwnProfileException extends RuntimeException {

    public NotOwnProfileException(long requestedApplicantId) {
        super("본인의 프로필만 조회할 수 있습니다. applicantId=" + requestedApplicantId);
    }
}
