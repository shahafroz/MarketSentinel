package com.marketsentinel.market;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A trading account at the broker. ownerRef links accounts that belong to the same person or entity. */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String accountRef;

    @Column(nullable = false)
    private String ownerRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AccountType accountType;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Account() {
    }

    public Account(UUID tenantId, String accountRef, String ownerRef, AccountType accountType) {
        this.tenantId = tenantId;
        this.accountRef = accountRef;
        this.ownerRef = ownerRef;
        this.accountType = accountType;
    }

    public Long getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public String getAccountRef() { return accountRef; }
    public String getOwnerRef() { return ownerRef; }
    public AccountType getAccountType() { return accountType; }

    public void update(String ownerRef, AccountType accountType) {
        this.ownerRef = ownerRef;
        this.accountType = accountType;
    }
}
