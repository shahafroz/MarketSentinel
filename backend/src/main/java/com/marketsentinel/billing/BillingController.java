package com.marketsentinel.billing;

import com.marketsentinel.security.CurrentUser;
import com.marketsentinel.tenant.Plan;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private final BillingService billing;

    public BillingController(BillingService billing) {
        this.billing = billing;
    }

    public record CheckoutRequest(@NotNull Plan plan) {
    }

    @GetMapping
    public BillingService.BillingView view() {
        return billing.view(CurrentUser.get().tenantId());
    }

    @PostMapping("/checkout")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> checkout(@Valid @RequestBody CheckoutRequest request) {
        return Map.of("url", billing.checkoutUrl(CurrentUser.get().tenantId(), request.plan()));
    }

    @PostMapping("/portal")
    @PreAuthorize("hasRole('ADMIN')")
    public Map<String, String> portal() {
        return Map.of("url", billing.portalUrl(CurrentUser.get().tenantId()));
    }

    /** Called by Stripe, not by users. The raw body is needed to verify Stripe's signature. */
    @PostMapping("/webhook")
    public ResponseEntity<Void> webhook(@RequestBody String payload,
            @RequestHeader("Stripe-Signature") String signature) {
        billing.handleWebhook(payload, signature);
        return ResponseEntity.ok().build();
    }
}
