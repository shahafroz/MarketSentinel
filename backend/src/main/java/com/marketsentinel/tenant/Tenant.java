package com.marketsentinel.tenant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/** A customer of the SaaS: one broker-dealer firm. All other data belongs to exactly one tenant. */
@Entity
@Table(name = "tenants")
public class Tenant {

    @Id
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Plan plan = Plan.FREE;

    private String stripeCustomerId;
    private String stripeSubscriptionId;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    protected Tenant() {
    }

    public Tenant(String name) {
        this.id = UUID.randomUUID();
        this.name = name;
    }

    public UUID getId() { return id; }
    public String getName() { return name; }
    public Plan getPlan() { return plan; }
    public String getStripeCustomerId() { return stripeCustomerId; }
    public String getStripeSubscriptionId() { return stripeSubscriptionId; }
    public Instant getCreatedAt() { return createdAt; }

    public void setPlan(Plan plan) { this.plan = plan; }
    public void setStripeCustomerId(String id) { this.stripeCustomerId = id; }
    public void setStripeSubscriptionId(String id) { this.stripeSubscriptionId = id; }
}
