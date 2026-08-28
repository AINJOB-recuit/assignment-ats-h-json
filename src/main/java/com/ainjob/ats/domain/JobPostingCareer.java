package com.ainjob.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * 공고가 요구하는 직무별 최소 경력연수.
 *
 * <p>"3년 이상" 같은 수치를 쿼리에 하드코딩하지 않기 위한 테이블이다.
 * 여러 직무를 요구하면 전부 충족해야 한다(AND).
 */
@Entity
@Table(name = "job_posting_career")
public class JobPostingCareer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_career_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "position_type_id", nullable = false)
    private PositionType positionType;

    @Column(name = "career_years", nullable = false)
    private short careerYears;

    protected JobPostingCareer() {
    }

    JobPostingCareer(JobPosting jobPosting, PositionType positionType, short careerYears) {
        this.jobPosting = jobPosting;
        this.positionType = positionType;
        this.careerYears = careerYears;
    }

    public Long getId() {
        return id;
    }

    public PositionType getPositionType() {
        return positionType;
    }

    public short getCareerYears() {
        return careerYears;
    }
}
