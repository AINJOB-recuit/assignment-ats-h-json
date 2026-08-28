package com.ainjob.ats.auth;

import com.ainjob.ats.common.ApiErrorResponse;
import com.ainjob.ats.common.ErrorCode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

/**
 * 인증 실패(401)를 나머지 API와 같은 오류 본문으로 내려준다.
 *
 * <p>기본 동작은 빈 본문 + {@code WWW-Authenticate} 헤더뿐이라, 클라이언트가 원인을 알 수 없다.
 */
public class RestAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    public RestAuthenticationEntryPoint(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        boolean tokenPresent = authException instanceof OAuth2AuthenticationException;
        ErrorCode code = tokenPresent ? ErrorCode.INVALID_TOKEN : ErrorCode.TOKEN_REQUIRED;
        String message = tokenPresent
                ? "액세스 토큰이 유효하지 않거나 만료되었습니다."
                : "액세스 토큰이 없습니다. Authorization: Bearer {token} 헤더가 필요합니다.";

        response.setStatus(code.status().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getOutputStream(),
                ApiErrorResponse.of(code, message, request.getRequestURI()));
    }
}
