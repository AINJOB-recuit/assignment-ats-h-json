package com.ainjob.ats.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 지원자(구직자 회원) Aggregate 루트.
 *
 * <p><b>글로벌 풀이다 — company_id 가 없다.</b> 한 지원자가 여러 기업에 동시 지원할 수 있어야 하므로
 * (더미의 문지후 — 한 지원자가 여러 기업에 지원한다) 지원자는 특정 테넌트에 귀속되지 않는다.
 * 테넌트 귀속은 지원 시점에 {@link Application} 이 갖는다.
 *
 * <p><b>동시에 로그인 주체다.</b> {@link CompanyUser} 와 나란히 서는 두 번째 인증 주체이며,
 * 테이블이 갈려 있으므로 한쪽 계정이 다른 쪽 권한을 얻을 경로가 없다. 본인 프로필 조회와
 * 지원 접수는 이 계정의 토큰으로만 할 수 있다.
 *
 * <p>학력·경력은 지원자 없이 존재할 수 없으므로 이 루트를 통해서만 만들고 지운다
 * (cascade + orphanRemoval).
 */
@Entity
@Table(name = "applicant")
public class Applicant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "applicant_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    /** 로그인 식별자 겸 알림 수신 주소. 전역 UNIQUE(uq_applicant_email). */
    @Column(name = "email", nullable = false, length = 120)
    private String email;

    /** {bcrypt}$2a$10$... 형태의 인코딩된 해시. 평문은 이 클래스에 들어오지 않는다. */
    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "gender")
    private Boolean gender;

    /** 지원 이력 보존을 위해 하드 삭제 대신 비활성화한다. 비활성 계정은 로그인할 수 없다. */
    @Column(name = "is_active", nullable = false)
    private boolean active = true;

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Education> educations = new ArrayList<>();

    @OneToMany(mappedBy = "applicant", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<Career> careers = new ArrayList<>();

    protected Applicant() {
    }

    /**
     * @param passwordHash 이미 인코딩된 해시. 인코딩 책임은 서비스에 있고, 이 생성자는 평문을 받지 않는다
     */
    public Applicant(String name, String email, String passwordHash, LocalDate birthDate, Boolean gender) {
        this.name = name;
        this.email = email;
        this.passwordHash = passwordHash;
        this.birthDate = birthDate;
        this.gender = gender;
        this.active = true;
    }

    /** 학력 추가. 같은 학위를 두 번 등록하면 DB 의 uq_education 이 최종적으로 거부한다. */
    public Education addEducation(DegreeLevel degreeLevel, Major major, String schoolName) {
        Education education = new Education(this, degreeLevel, major, schoolName);
        educations.add(education);
        return education;
    }

    /** 경력 추가. 반환된 경력에 스킬을 붙인다 — {@link Career#addSkill(Skill)}. */
    public Career addCareer(PositionType positionType, String companyName,
                            LocalDateTime startAt, LocalDateTime endAt) {
        Career career = new Career(this, positionType, companyName, startAt, endAt);
        careers.add(career);
        return career;
    }

    /**
     * 특정 직무의 총 경력 연수.
     *
     * <p>과제 SQL과 계산 방식을 일치시킨다 — 경력마다 연 단위로 끊어 더하면 11개월짜리가 전부
     * 0년이 되므로, <b>월 단위로 합산한 뒤 마지막에 12로 나눈다.</b>
     * 재직 중(end_dt 가 NULL)이면 기준 시각까지로 본다.
     *
     * <p><b>경력 한 건의 개월수는 음수가 되지 않는다</b> — {@link #monthsOf} 가 보증한다.
     * 입력 단계에서 미래 날짜를 막지 않는 대신(입사 예정 경력을 미리 등록하는 것은 정상이다)
     * 여기서 규칙으로 처리한다. 이 전제가 깨지면 자바의 {@code /}(0 방향 절단)와 HQL 번역의
     * 부등식(floor 전제)이 음수 구간에서 갈려 <b>합격자 필터 두 구현의 결과가 달라진다</b> —
     * 근거는 {@code PassedApplicantQueryRepository} 주석에 있다. 그래서 같은 규칙이
     * 세 곳(여기 · HQL · 네이티브 SQL)에 <b>모두</b> 들어가 있어야 한다.
     */
    public int careerYearsOf(short positionTypeId, LocalDateTime now) {
        long months = careers.stream()
                .filter(c -> c.getPositionType().getId() == positionTypeId)
                .mapToLong(c -> monthsOf(c, now))
                .sum();
        return (int) (months / 12);
    }

    /**
     * 경력 한 건이 기여하는 개월수. <b>절대 음수가 되지 않는다.</b>
     *
     * <p>두 경우를 0 으로 본다.
     * <ul>
     *   <li><b>아직 시작하지 않은 경력</b>({@code startAt} 이 기준 시각보다 미래) — 입사 예정으로
     *       등록해 둔 건이다. 아직 일하지 않았으므로 0 이다.</li>
     *   <li><b>기간이 뒤집힌 경력</b>({@code endAt} 이 {@code startAt} 보다 이름) — 등록 API 는
     *       {@code isPeriodValid()} 로 막지만, 더미 적재나 직접 INSERT 로 들어올 수 있다.</li>
     * </ul>
     *
     * <p>종료일이 미래인 경력(퇴사 예정)은 그대로 센다 — 음수가 아니고, 재직 중과 같은 취급이다.
     */
    private static long monthsOf(Career career, LocalDateTime now) {
        LocalDateTime startAt = career.getStartAt();
        LocalDateTime endAt = career.endAtOr(now);
        if (startAt.isAfter(now) || endAt.isBefore(startAt)) {
            return 0;
        }
        return ChronoUnit.MONTHS.between(startAt, endAt);
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public Boolean getGender() {
        return gender;
    }

    public List<Education> getEducations() {
        return Collections.unmodifiableList(educations);
    }

    public List<Career> getCareers() {
        return Collections.unmodifiableList(careers);
    }
}
