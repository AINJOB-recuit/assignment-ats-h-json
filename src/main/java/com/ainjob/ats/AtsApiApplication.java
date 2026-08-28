package com.ainjob.ats;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * AINJOB ATS API.
 *
 * <p>개발 과제 요구사항
 * <ul>
 *   <li>1. ATS 합격자 필터 API — 과제 SQL(03_AINJOB_query.sql)을 엔드포인트로 구현, company_id 필터 포함</li>
 *   <li>2. company_id 없이 요청한 경우의 동작 정의 + 테스트 케이스</li>
 *   <li>3. (가점) 지원자 상태 변경 시 이메일 알림 발송</li>
 * </ul>
 */
@SpringBootApplication
@ConfigurationPropertiesScan
@EnableAsync
public class AtsApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(AtsApiApplication.class, args);
    }
}
