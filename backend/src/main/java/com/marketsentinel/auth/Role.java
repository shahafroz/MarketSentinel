package com.marketsentinel.auth;

/**
 * ANALYST works alerts and cases. SUPERVISOR can also escalate, approve AI summaries and reassign.
 * AUDITOR is read-only (including the audit log). ADMIN manages users, API keys and billing.
 */
public enum Role {
    ANALYST,
    SUPERVISOR,
    AUDITOR,
    ADMIN
}
