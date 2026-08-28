package com.ainjob.ats.applicant;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 합격자 필터 실행 방식 설정.
 *
 * <pre>
 * ainjob:
 *   passed-applicant:
 *     strategy: JPA        # 또는 NATIVE_SQL
 * </pre>
 *
 * @param strategy 비워 두면 {@link PassedApplicantStrategy#JPA}
 */
@ConfigurationProperties(prefix = "ainjob.passed-applicant")
public record PassedApplicantProperties(PassedApplicantStrategy strategy) {

    public PassedApplicantProperties {
        if (strategy == null) {
            strategy = PassedApplicantStrategy.JPA;
        }
    }
}
