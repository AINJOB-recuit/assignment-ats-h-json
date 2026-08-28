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

/** 지원자의 학력 1건. 스키마상 지원자당 학위별 1행이다(uq_education). */
@Entity
@Table(name = "education")
public class Education {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "education_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "applicant_id", nullable = false)
    private Applicant applicant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "degree_level_id", nullable = false)
    private DegreeLevel degreeLevel;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "major_id", nullable = false)
    private Major major;

    /** 학교명. 컬럼명이 name 이라 필드명으로 의미를 살린다. */
    @Column(name = "name", nullable = false, length = 100)
    private String schoolName;

    protected Education() {
    }

    Education(Applicant applicant, DegreeLevel degreeLevel, Major major, String schoolName) {
        this.applicant = applicant;
        this.degreeLevel = degreeLevel;
        this.major = major;
        this.schoolName = schoolName;
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

    public String getSchoolName() {
        return schoolName;
    }
}
