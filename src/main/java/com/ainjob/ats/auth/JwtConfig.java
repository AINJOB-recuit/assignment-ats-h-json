package com.ainjob.ats.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import java.security.SecureRandom;
import java.util.Base64;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * 대칭키(HS256) 기반 JWT 발급/검증 빈.
 *
 * <p>과제 범위에서는 발급자와 검증자가 같은 애플리케이션이므로 대칭키로 충분하다.
 * 인증 서버가 분리되면 이 설정만 비대칭키(RS256) + JWK Set URI 로 바꾸면 되고,
 * 나머지 코드는 {@link org.springframework.security.oauth2.jwt.Jwt} 를 그대로 받는다.
 */
@Configuration
public class JwtConfig {

    private static final Logger log = LoggerFactory.getLogger(JwtConfig.class);
    private static final String KEY_ALGORITHM = "HmacSHA256";
    private static final int KEY_BYTES = 32;

    @Bean
    SecretKeySpec jwtSigningKey(AuthProperties properties) {
        String configured = properties.jwtSecret();
        if (configured != null && !configured.isBlank()) {
            if (configured.getBytes(java.nio.charset.StandardCharsets.UTF_8).length < KEY_BYTES) {
                throw new IllegalStateException("ainjob.auth.jwt-secret 은 최소 " + KEY_BYTES + "바이트여야 합니다.");
            }
            return new SecretKeySpec(configured.getBytes(java.nio.charset.StandardCharsets.UTF_8), KEY_ALGORITHM);
        }

        byte[] generated = new byte[KEY_BYTES];
        new SecureRandom().nextBytes(generated);
        log.warn("ainjob.auth.jwt-secret 이 설정되지 않아 임의 서명 키를 생성했습니다. "
                + "재기동하면 기존 토큰은 모두 무효가 됩니다. ainjob.auth.jwt-secret 을 설정 파일에 지정하세요. "
                + "(참고용 생성 키: {})", Base64.getEncoder().encodeToString(generated));
        return new SecretKeySpec(generated, KEY_ALGORITHM);
    }

    @Bean
    JwtEncoder jwtEncoder(SecretKeySpec key) {
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    @Bean
    JwtDecoder jwtDecoder(SecretKeySpec key) {
        return NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
    }
}
