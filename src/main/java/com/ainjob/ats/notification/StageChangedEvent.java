package com.ainjob.ats.notification;

import java.time.OffsetDateTime;

/**
 * 지원자 상태 변경 도메인 이벤트.
 *
 * <p>알림은 전이 트랜잭션의 부가 관심사다. 커밋된 뒤에만(AFTER_COMMIT) 비동기로 발송해
 * 메일 서버 장애가 상태 전이를 롤백시키지 않도록 분리한다.
 */
public record StageChangedEvent(
        long applicationId,
        long companyId,
        String companyName,
        String jobPostingTitle,
        String applicantName,
        String applicantEmail,
        String fromStageName,
        String toStageName,
        String reason,
        String changedBy,
        OffsetDateTime changedAt) {
}
