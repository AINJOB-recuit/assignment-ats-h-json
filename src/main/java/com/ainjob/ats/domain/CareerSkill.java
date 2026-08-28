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

/** 경력에서 사용한 스킬. 공고 필수스킬 충족 판정의 원천 데이터다. */
@Entity
@Table(name = "career_skill")
public class CareerSkill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "career_skill_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "career_id", nullable = false)
    private Career career;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "skill_id", nullable = false)
    private Skill skill;

    protected CareerSkill() {
    }

    CareerSkill(Career career, Skill skill) {
        this.career = career;
        this.skill = skill;
    }

    public Long getId() {
        return id;
    }

    public Skill getSkill() {
        return skill;
    }
}
