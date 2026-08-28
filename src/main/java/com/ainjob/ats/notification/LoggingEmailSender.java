package com.ainjob.ats.notification;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 기본 구현체. SMTP 서버 없이도 알림 흐름(이벤트 → 발송)이 동작하는지 로그로 확인할 수 있게 한다.
 * {@code ainjob.mail.enabled=true} 로 SMTP 구현체가 올라오면 이 빈은 등록되지 않는다.
 */
@Component
@ConditionalOnProperty(prefix = "ainjob.mail", name = "enabled", havingValue = "false", matchIfMissing = true)
public class LoggingEmailSender implements EmailSender {

    private static final Logger log = LoggerFactory.getLogger(LoggingEmailSender.class);

    @Override
    public void send(EmailMessage message) {
        log.info("[MAIL:DRY-RUN] to={} subject={}\n{}", message.to(), message.subject(), message.body());
    }
}
