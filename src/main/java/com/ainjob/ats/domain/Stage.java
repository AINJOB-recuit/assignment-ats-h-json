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
import java.time.LocalDateTime;

/**
 * 상태 전이 이력 1행 — 누가 / 언제 / 어느 단계로 / 왜.
 *
 * <p><b>이력성 데이터다.</b> 한 번 쌓이면 고치거나 지우지 않는다(append-only). 그래서 이 엔티티에는
 * setter 가 없고, 생성은 {@link Application} 을 통해서만 가능하다.
 *
 * <p>처리자를 문자열이 아니라 FK 로 묶어 "누가 했는지"를 DB 가 보장한다. 담당자가 퇴사해도 이력이
 * 남아야 하므로 {@link CompanyUser} 는 하드 삭제하지 않고 비활성화한다.
 *
 * <p><b>{@code createdBy} 가 null 인 행이 하나 있다</b> — 구직자가 스스로 지원하며 만든 첫 단계
 * (서류접수)다. 그 시점에는 관여한 담당자가 없으므로 채울 값이 없다. 즉 null = 구직자 본인 행위,
 * non-null = 기업 담당자 행위이며, 누가 지원했는지는 {@link Application#getApplicant()} 가 갖는다.
 */
@Entity
@Table(name = "stage")
public class Stage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "stage_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    /** 이 시점에 도달한 단계. 문자열 상태값 대신 lookup FK. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "stage_type_id", nullable = false)
    private StageType stageType;

    /** 전이 사유/메모. */
    // TEXT 컬럼이다. @Lob 을 쓰면 Hibernate 가 LONGTEXT/CLOB 을 기대해
    // ddl-auto=validate 가 스키마와 어긋난다고 판단할 수 있으므로 타입을 명시한다.
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /** 처리자. 구직자 본인이 만든 첫 단계에서는 null 이다 — 클래스 주석 참고. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by")
    private CompanyUser createdBy;

    @Column(name = "created_dt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected Stage() {
    }

    Stage(Application application, StageType stageType, String content,
          CompanyUser createdBy, LocalDateTime createdAt) {
        this.application = application;
        this.stageType = stageType;
        this.content = content;
        this.createdBy = createdBy;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public StageType getStageType() {
        return stageType;
    }

    public String getContent() {
        return content;
    }

    public CompanyUser getCreatedBy() {
        return createdBy;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
