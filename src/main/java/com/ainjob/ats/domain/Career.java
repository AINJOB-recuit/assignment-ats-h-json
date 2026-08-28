package com.ainjob.ats.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** 지원자의 경력 1건. 이 경력에서 사용한 스킬을 {@link CareerSkill} 로 갖는다. */
@Entity
@Table(name = "career")
public class Career {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "career_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    /** 이 경력의 직무(BE/FE). 공고의 요구 경력과 직무 단위로 대조된다. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_type_id", nullable = false)
    private PositionType positionType;

    /** 직장명. 컬럼명이 name 이라 필드명으로 의미를 살린다. */
    @Column(name = "name", nullable = false, length = 100)
    private String companyName;

    @Column(name = "start_dt", nullable = false)
    private LocalDateTime startAt;

    /** NULL 이면 재직 중. */
    @Column(name = "end_dt")
    private LocalDateTime endAt;

    @OneToMany(mappedBy = "career", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<CareerSkill> careerSkills = new ArrayList<>();

    protected Career() {
    }

    Career(Applicant applicant, PositionType positionType, String companyName,
           LocalDateTime startAt, LocalDateTime endAt) {
        this.applicant = applicant;
        this.positionType = positionType;
        this.companyName = companyName;
        this.startAt = startAt;
        this.endAt = endAt;
    }

    /** 같은 경력에 같은 스킬을 두 번 넣으면 DB 의 uq_career_skill 이 최종적으로 거부한다. */
    public void addSkill(Skill skill) {
        careerSkills.add(new CareerSkill(this, skill));
    }

    /** 재직 중이면 기준 시각을, 아니면 퇴사일을 돌려준다. 경력 개월수 계산의 종료점. */
    public LocalDateTime endAtOr(LocalDateTime now) {
        return endAt == null ? now : endAt;
    }

    public Long getId() {
        return id;
    }

    public PositionType getPositionType() {
        return positionType;
    }

    public String getCompanyName() {
        return companyName;
    }

    public LocalDateTime getStartAt() {
        return startAt;
    }

    public LocalDateTime getEndAt() {
        return endAt;
    }

    public List<CareerSkill> getCareerSkills() {
        return Collections.unmodifiableList(careerSkills);
    }
}
