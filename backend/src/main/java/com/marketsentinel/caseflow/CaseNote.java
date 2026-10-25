package com.marketsentinel.caseflow;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "case_notes")
public class CaseNote {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private Long caseId;

    @Column(nullable = false)
    private String author;

    @Column(nullable = false)
    private String body;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected CaseNote() {
    }

    public CaseNote(UUID tenantId, Long caseId, String author, String body) {
        this.tenantId = tenantId;
        this.caseId = caseId;
        this.author = author;
        this.body = body;
    }

    public Long getId() { return id; }
    public Long getCaseId() { return caseId; }
    public String getAuthor() { return author; }
    public String getBody() { return body; }
    public Instant getCreatedAt() { return createdAt; }
}
