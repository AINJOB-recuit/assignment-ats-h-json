package com.ainjob.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 학위 마스터.
 *
 * <p>{@code grade} 는 '학사 이상' 같은 비교를 문자열 대신 숫자로 하기 위한 등급이다(학사 &lt; 석사 &lt; 박사).
 */
@Entity
@Table(name = "degree_level")
public class DegreeLevel {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "degree_level_id")
    private Short id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 20)
    private String name;

    @Column(name = "grade", nullable = false)
    private short grade;

    protected DegreeLevel() {
    }

    public Short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getGrade() {
        return grade;
    }
}
