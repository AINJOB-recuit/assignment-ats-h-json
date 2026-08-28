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
 * 공고가 요구하는 (학위, 전공) 조합 1건.
 *
 * <p>여러 건을 등록하면 OR 로 판정한다 — "컴퓨터공학 학사 <b>또는</b> 소프트웨어공학 학사".
 */
@Entity
@Table(name = "job_posting_education")
public class JobPostingEducation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_education_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "degree_level_id", nullable = false)
    private DegreeLevel degreeLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    protected JobPostingEducation() {
    }

    JobPostingEducation(JobPosting jobPosting, DegreeLevel degreeLevel, Major major) {
        this.jobPosting = jobPosting;
        this.degreeLevel = degreeLevel;
        this.major = major;
    }

    public Long getId() {
        return id;
    }

    public DegreeLevel getDegreeLevel() {
        return degreeLevel;
    }

    public Major getMajor() {
        return major;
    }
}
