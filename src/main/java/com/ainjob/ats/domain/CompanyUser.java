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
 * 기업 소속 채용담당자 = 인증 주체이자 {@code stage.created_by} 의 대상.
 *
 * <p>테넌트 사칭이 불가능한 이유가 이 엔티티에 있다. {@code companyId} 는 요청이 아니라
 * <b>로그인에 성공한 이 행</b>에서 결정된다. 이메일이 전역 UNIQUE 이므로
 * {email, password} 만으로 소속 기업까지 확정된다.
 *
 * <p>담당자는 하드 삭제하지 않는다. 상태 전이 이력이 이 행을 FK 로 참조하므로,
 * 퇴사 시에는 {@code active=false} 로 두어 감사 로그를 보존한다.
 */
@Entity
@Table(name = "company_user")
public class CompanyUser {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "company_user_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "role_id", nullable = false)
    private CompanyRole role;

    @Column(name = "email", nullable = false, length = 120)
    private String email;

    @Column(name = "password_hash", nullable = false, length = 100)
    private String passwordHash;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "created_dt", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    protected CompanyUser() {
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    /** 소속 기업 PK. 연관 엔티티를 초기화하지 않도록 프록시의 식별자만 읽는다. */
    public long getCompanyId() {
        return company.getId();
    }

    public CompanyRole getRole() {
        return role;
    }

    public String getRoleCode() {
        return role.getCode();
    }

    public String getEmail() {
        return email;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public String getName() {
        return name;
    }

    public boolean isActive() {
        return active;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
