package com.ainjob.ats.auth;

import com.ainjob.ats.domain.CompanyUser;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/** 로그인 주체 조회. */
public interface CompanyUserRepository extends JpaRepository<CompanyUser, Long> {

    /**
     * 이메일은 전역 UNIQUE 이므로 소속 기업까지 한 번에 결정된다 — 로그인 요청에 company_id 가
     * 필요 없는 이유다.
     *
     * <p>역할 코드는 토큰 클레임에 바로 실리므로 지연 로딩으로 한 번 더 다녀오지 않도록 함께 가져온다.
     * 소속 기업은 PK 만 쓰므로 프록시로 둔다.
     */
    @EntityGraph(attributePaths = "role")
    Optional<CompanyUser> findByEmail(String email);
}
