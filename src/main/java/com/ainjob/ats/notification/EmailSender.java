package com.ainjob.ats.notification;

/**
 * 메일 발송 포트.
 *
 * <p>구현체는 SMTP({@link SmtpEmailSender})와 로깅 스텁({@link LoggingEmailSender})이 있다.
 * AWS SES로 바꿀 때도 이 인터페이스 구현체만 추가하면 되고 도메인 코드는 손대지 않는다.
 */
public interface EmailSender {

    void send(EmailMessage message);
}
