package com.ainjob.ats.notification;

import java.time.format.DateTimeFormatter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ainjob.ats.config.AsyncConfig;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 요구사항 3 (가점) — 지원자 상태 변경 시 이메일 알림 발송.
 *
 * <p>{@link TransactionPhase#AFTER_COMMIT} + {@link Async} 조합으로
 * <ul>
 *   <li>롤백된 전이에 대해서는 메일이 나가지 않고,</li>
 *   <li>메일 발송 실패가 상태 전이 트랜잭션을 되돌리지 않는다.</li>
 * </ul>
 *
 * <p><b>실행 풀을 이름으로 지정한다.</b> 한정자를 비우면 컨텍스트의 기본 실행기로 가는데,
 * 그러면 MVC 비동기 요청 처리나 나중에 추가될 다른 {@code @Async} 작업과 스레드를 나눠 쓰게 된다.
 * SMTP 타임아웃이 5초라 메일 서버가 느려지면 무관한 작업까지 밀리므로,
 * {@link AsyncConfig#NOTIFY_EXECUTOR} 전용 풀에 묶어 둔다.
 */
@Component
public class StageChangedEmailListener {

    private static final Logger log = LoggerFactory.getLogger(StageChangedEmailListener.class);
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final EmailSender emailSender;

    public StageChangedEmailListener(EmailSender emailSender) {
        this.emailSender = emailSender;
    }

    @Async(AsyncConfig.NOTIFY_EXECUTOR)
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void on(StageChangedEvent event) {
        try {
            emailSender.send(new EmailMessage(event.applicantEmail(), subject(event), body(event)));
        } catch (Exception e) {
            // 알림 실패는 전이 결과에 영향을 주지 않는다. 재발송은 별도 배치/DLQ의 책임.
            log.error("상태 변경 알림 발송 실패. applicationId={}, to={}",
                    event.applicationId(), event.applicantEmail(), e);
        }
    }

    private String subject(StageChangedEvent event) {
        return "[%s] %s 전형 상태가 '%s'(으)로 변경되었습니다."
                .formatted(event.companyName(), event.jobPostingTitle(), event.toStageName());
    }

    private String body(StageChangedEvent event) {
        StringBuilder sb = new StringBuilder();
        sb.append(event.applicantName()).append("님, 안녕하세요.\n\n")
          .append("지원하신 전형의 진행 상태가 변경되었습니다.\n\n")
          .append("- 기업     : ").append(event.companyName()).append('\n')
          .append("- 공고     : ").append(event.jobPostingTitle()).append('\n')
          .append("- 변경 전  : ").append(event.fromStageName()).append('\n')
          .append("- 변경 후  : ").append(event.toStageName()).append('\n')
          .append("- 변경 일시: ").append(event.changedAt().format(TIMESTAMP)).append('\n');
        if (event.reason() != null && !event.reason().isBlank()) {
            sb.append("- 안내     : ").append(event.reason()).append('\n');
        }
        sb.append("\n감사합니다.\nAINJOB 드림");
        return sb.toString();
    }
}
