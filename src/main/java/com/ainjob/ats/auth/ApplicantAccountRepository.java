package com.ainjob.ats.auth;

import com.ainjob.ats.domain.Applicant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 구직자 <b>계정</b> 저장소 — 로그인 시 이메일로 계정을 찾는 것이 유일한 용도다.
 *
 * <p>{@code applicant} 패키지의 {@code ApplicantRepository} 와 대상 엔티티는 같지만 관심사가 다르다.
 * 그쪽은 프로필·학력·경력 유스케이스를, 이쪽은 인증만 본다. 이렇게 갈라 두면 {@code auth} 패키지가
 * 도메인 외의 다른 패키지에 의존하지 않는다 — {@link CompanyUserRepository} 가 여기 있는 것과 같은 이유다.
 */
public interface ApplicantAccountRepository extends JpaRepository<Applicant, Long> {

    /** uq_applicant_email 로 유일성이 보장되므로 결과는 0 또는 1건이다. */
    Optional<Applicant> findByEmail(String email);
}
