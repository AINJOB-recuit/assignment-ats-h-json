package com.ainjob.ats.applicant;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import org.springframework.core.io.ClassPathResource;

/**
 * classpath의 .sql 파일을 읽어들인다.
 *
 * <p>과제 SQL을 자바 문자열로 옮겨 적지 않고 원본 파일 그대로 두기 위한 장치다.
 * 제출한 SQL과 API가 실행하는 SQL이 갈라지지 않는다.
 */
final class SqlLoader {

    private SqlLoader() {
    }

    static String load(String classpathLocation) {
        try (var in = new ClassPathResource(classpathLocation).getInputStream()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("SQL 리소스를 읽을 수 없습니다: " + classpathLocation, e);
        }
    }
}
