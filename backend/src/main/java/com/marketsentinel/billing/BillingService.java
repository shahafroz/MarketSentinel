package com.marketsentinel.billing;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.config.SentinelProperties;
import com.marketsentinel.tenant.Plan;
import com.marketsentinel.tenant.Tenant;
import com.marketsentinel.tenant.TenantRepository;
import com.marketsentinel.web.ApiException;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.model.StripeObject;
import com.stripe.model.Subscription;
import com.stripe.model.checkout.Session;
import com.stripe.net.RequestOptions;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Subscriptions with Stripe (use test-mode keys). The browser never tells us a payment succeeded:
 * only a webhook signed by Stripe can change a firm's plan, and each webhook event is applied once.
 */
@Service
public class BillingService {

    private static final Logger log = LoggerFactory.getLogger(BillingService.class);

    private final SentinelProperties.Billing config;
    private final TenantRepository tenants;
    private final UsageService usage;
    private final JdbcTemplate jdbc;
    private final AuditService audit;

    public BillingService(SentinelProperties props, TenantRepository tenants, UsageService usage, JdbcTemplate jdbc,
            AuditService audit) {
        this.config = props.billing();
        this.tenants = tenants;
        this.usage = usage;
        this.jdbc = jdbc;
        this.audit = audit;
    }

    public record BillingView(String plan, long tradesThisMonth, long monthlyLimit, boolean stripeEnabled,
            boolean hasSubscription) {
    }

    public BillingView view(UUID tenantId) {
        Tenant t = tenants.findById(tenantId).orElseThrow();
        long limit = t.getPlan().monthlyTradeLimit();
        return new BillingView(t.getPlan().name(), usage.tradesThisMonth(tenantId), limit == Long.MAX_VALUE ? -1 : limit,
                config.enabled(), t.getStripeSubscriptionId() != null);
    }

    public String checkoutUrl(UUID tenantId, Plan plan) {
        requireStripe();
        String price = switch (plan) {
            case PRO -> config.pricePro();
            case ENTERPRISE -> config.priceEnterprise();
            case FREE -> throw new IllegalArgumentException("The Free plan needs no checkout");
        };
        if (price == null || price.isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "No Stripe price configured for " + plan);
        }
        Tenant t = tenants.findById(tenantId).orElseThrow();
        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                .addLineItem(SessionCreateParams.LineItem.builder().setPrice(price).setQuantity(1L).build())
                .setClientReferenceId(tenantId.toString())
                .putMetadata("tenant", tenantId.toString())
                .putMetadata("plan", plan.name())
                .setSuccessUrl(config.appUrl() + "/billing?status=success")
                .setCancelUrl(config.appUrl() + "/billing?status=cancelled");
        if (t.getStripeCustomerId() != null) {
            params.setCustomer(t.getStripeCustomerId());
        }
        try {
            return Session.create(params.build(), options()).getUrl();
        } catch (StripeException e) {
            log.warn("Stripe checkout failed: {}", e.getMessage());
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe error: " + e.getMessage());
        }
    }

    public String portalUrl(UUID tenantId) {
        requireStripe();
        Tenant t = tenants.findById(tenantId).orElseThrow();
        if (t.getStripeCustomerId() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "No Stripe customer yet: subscribe first");
        }
        try {
            var params = com.stripe.param.billingportal.SessionCreateParams.builder()
                    .setCustomer(t.getStripeCustomerId()).setReturnUrl(config.appUrl() + "/billing").build();
            return com.stripe.model.billingportal.Session.create(params, options()).getUrl();
        } catch (StripeException e) {
            throw new ApiException(HttpStatus.BAD_GATEWAY, "Stripe error: " + e.getMessage());
        }
    }

    /** Verifies the signature, skips events already processed, then applies the change. */
    @Transactional
    public void handleWebhook(String payload, String signature) {
        if (config.stripeWebhookSecret() == null || config.stripeWebhookSecret().isBlank()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Stripe webhooks are not configured");
        }
        Event event;
        try {
            event = Webhook.constructEvent(payload, signature, config.stripeWebhookSecret());
        } catch (SignatureVerificationException e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid Stripe signature");
        }
        int inserted = jdbc.update("INSERT INTO processed_webhook_events (event_id) VALUES (?) ON CONFLICT DO NOTHING",
                event.getId());
        if (inserted == 0) {
            log.info("Stripe event {} already processed, skipping", event.getId());
            return;
        }
        StripeObject object = event.getDataObjectDeserializer().getObject()
                .orElseGet(() -> deserializeUnsafe(event));
        switch (event.getType()) {
            case "checkout.session.completed" -> onCheckoutCompleted((Session) object);
            case "customer.subscription.created", "customer.subscription.updated" -> onSubscriptionChanged((Subscription) object);
            case "customer.subscription.deleted" -> onSubscriptionDeleted((Subscription) object);
            default -> log.debug("Ignoring Stripe event {}", event.getType());
        }
    }

    private void onCheckoutCompleted(Session session) {
        if (session.getClientReferenceId() == null) {
            return;
        }
        tenants.findById(UUID.fromString(session.getClientReferenceId())).ifPresent(t -> {
            t.setStripeCustomerId(session.getCustomer());
            t.setStripeSubscriptionId(session.getSubscription());
            String plan = session.getMetadata() == null ? null : session.getMetadata().get("plan");
            if (plan != null) {
                changePlan(t, Plan.valueOf(plan), "checkout completed");
            }
        });
    }

    private void onSubscriptionChanged(Subscription sub) {
        tenantFor(sub).ifPresent(t -> {
            t.setStripeSubscriptionId(sub.getId());
            boolean active = "active".equals(sub.getStatus()) || "trialing".equals(sub.getStatus());
            String price = sub.getItems().getData().isEmpty() ? null : sub.getItems().getData().getFirst().getPrice().getId();
            Plan plan = !active ? Plan.FREE
                    : price != null && price.equals(config.priceEnterprise()) ? Plan.ENTERPRISE : Plan.PRO;
            changePlan(t, plan, "subscription " + sub.getStatus());
        });
    }

    private void onSubscriptionDeleted(Subscription sub) {
        tenantFor(sub).ifPresent(t -> {
            t.setStripeSubscriptionId(null);
            changePlan(t, Plan.FREE, "subscription cancelled");
        });
    }

    private Optional<Tenant> tenantFor(Subscription sub) {
        return tenants.findByStripeCustomerId(sub.getCustomer());
    }

    private void changePlan(Tenant t, Plan plan, String why) {
        if (t.getPlan() != plan) {
            Plan old = t.getPlan();
            t.setPlan(plan);
            audit.record(t.getId(), "stripe", "PLAN_CHANGED", "tenant", t.getId(), old + " -> " + plan + " (" + why + ")");
        }
    }

    private static StripeObject deserializeUnsafe(Event event) {
        try {
            return event.getDataObjectDeserializer().deserializeUnsafe();
        } catch (Exception e) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Unreadable Stripe event");
        }
    }

    private void requireStripe() {
        if (!config.enabled()) {
            throw new ApiException(HttpStatus.SERVICE_UNAVAILABLE, "Billing is not configured (set STRIPE_SECRET_KEY)");
        }
    }

    private RequestOptions options() {
        return RequestOptions.builder().setApiKey(config.stripeSecretKey()).build();
    }
}
