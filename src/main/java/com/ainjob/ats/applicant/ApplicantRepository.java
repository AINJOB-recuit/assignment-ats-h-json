package com.ainjob.ats.applicant;

import com.ainjob.ats.domain.Applicant;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 지원자 저장소.
 *
 * <p>지원자는 글로벌 풀이라 테넌트 조건이 없다 — 한 지원자가 여러 기업에 지원할 수 있어야 한다.
 * 접근 통제는 저장소가 아니라 유스케이스가 건다: 구직자는 본인만, 기업은 자기 공고에 지원한
 * 지원자만 볼 수 있다({@link ApplicantService} / {@link CompanyApplicantService}).
 *
 * <p>로그인용 조회는 여기가 아니라 {@code auth} 패키지의 {@code ApplicantAccountRepository} 에 있다.
 */
public interface ApplicantRepository extends JpaRepository<Applicant, Long> {

    /** 이메일 전역 UNIQUE(uq_applicant_email) 선검사. 최종 보증은 DB 제약이다. */
    boolean existsByEmail(String email);
}
