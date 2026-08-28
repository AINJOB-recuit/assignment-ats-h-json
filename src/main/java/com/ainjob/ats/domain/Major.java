package com.ainjob.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 전공 마스터. 이 테이블만 비즈니스키가 code 가 아니라 name 이다(스키마의 uq_major_name). */
@Entity
@Table(name = "major")
public class Major {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "major_id")
    private Integer id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    protected Major() {
    }

    public Integer getId() {
        return id;
    }

    public String getName() {
        return name;
    }
}
