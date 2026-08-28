package com.ainjob.ats.auth;

/**
 * 로그인 실패 → 401.
 *
 * <p>"없는 계정", "비활성 계정", "비밀번호 불일치"를 구분하지 않는다.
 * 구분하면 가입 여부를 알아내는 계정 열거 공격이 가능해진다.
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("이메일 또는 비밀번호가 올바르지 않습니다.");
    }
}
