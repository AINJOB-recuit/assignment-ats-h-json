package com.ainjob.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 기업. {@code company_id} 가 곧 테넌트 식별자다. */
@Entity
@Table(name = "company")
public class Company {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_id")
    private Long id;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "biz_no", nullable = false, length = 20)
    private String bizNo;

    @Column(name = "location", nullable = false)
    private String location;

    protected Company() {
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getBizNo() {
        return bizNo;
    }

    public String getLocation() {
        return location;
    }
}
