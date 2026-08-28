package com.ainjob.ats.notification;

/** 발송 단위 메일 1건. */
public record EmailMessage(String to, String subject, String body) {
}
