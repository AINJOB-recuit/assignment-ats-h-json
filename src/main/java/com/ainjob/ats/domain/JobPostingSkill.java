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

/** 공고가 요구하는 필수스킬 1건. 판정은 AND(관계 나눗셈) — 전부 갖춰야 통과다. */
@Entity
@Table(name = "job_posting_skill")
public class JobPostingSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_posting_skill_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    protected JobPostingSkill() {
    }

    JobPostingSkill(JobPosting jobPosting, Skill skill) {
        this.jobPosting = jobPosting;
        this.skill = skill;
    }

    public Long getId() {
        return id;
    }

    public Skill getSkill() {
        return skill;
    }
}
