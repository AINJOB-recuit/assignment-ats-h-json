package com.ainjob.ats.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * 채용 단계 마스터 (APPLIED / INTERVIEW / HIRED / REJECTED).
 *
 * <p>상태값을 문자열이 아니라 PK 로 다루기 위한 lookup 테이블이다. 진행 순서·종결 여부·합격 여부도
 * 코드에 하드코딩하지 않고 {@code sortOrder} · {@code terminal} · {@code passed} 를 그대로 읽는다.
 * 단계가 추가돼도(예: 코딩테스트) 자바 코드는 고치지 않는다.
 */
@Entity
@Table(name = "stage_type")
public class StageType {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_type_id")
    private Short id;

    @Column(name = "code", nullable = false, length = 20)
    private String code;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private short sortOrder;

    @Column(name = "is_terminal", nullable = false)
    private boolean terminal;

    @Column(name = "is_passed", nullable = false)
    private boolean passed;

    protected StageType() {
    }

    /**
     * 마스터 행은 애플리케이션이 INSERT 하지 않는다. 이 생성자는 전이 규칙을 DB 없이 검증하는
     * 테스트 픽스처 전용이다.
     */
    public StageType(short id, String code, String name, short sortOrder, boolean terminal, boolean passed) {
        this.id = id;
        this.code = code;
        this.name = name;
        this.sortOrder = sortOrder;
        this.terminal = terminal;
        this.passed = passed;
    }

    public short getId() {
        return id;
    }

    public String getCode() {
        return code;
    }

    public String getName() {
        return name;
    }

    public short getSortOrder() {
        return sortOrder;
    }

    public boolean isTerminal() {
        return terminal;
    }

    public boolean isPassed() {
        return passed;
    }
}
