package com.marketsentinel.market;

import com.marketsentinel.market.IngestRequests.AccountRequest;
import com.marketsentinel.market.IngestRequests.CancelRequest;
import com.marketsentinel.market.IngestRequests.IngestResult;
import com.marketsentinel.market.IngestRequests.OrderRequest;
import com.marketsentinel.market.IngestRequests.OrderView;
import com.marketsentinel.market.IngestRequests.TradeRequest;
import com.marketsentinel.market.IngestRequests.TradeView;
import com.marketsentinel.security.CurrentUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Where broker systems send market data, authenticated with an API key (X-API-Key header).
 * 201 Created = stored now; 200 OK = an earlier request with the same Idempotency-Key was replayed.
 */
@RestController
@Validated
@RequestMapping("/api/v1/ingest")
public class IngestController {

    private final IngestService ingest;

    public IngestController(IngestService ingest) {
        this.ingest = ingest;
    }

    @PostMapping("/accounts")
    public Map<String, Integer> accounts(@RequestBody @Size(max = 1000) List<@Valid AccountRequest> accounts) {
        return Map.of("upserted", ingest.upsertAccounts(CurrentUser.get().tenantId(), accounts));
    }

    @PostMapping("/orders")
    public ResponseEntity<OrderView> placeOrder(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
            @Valid @RequestBody OrderRequest order) {
        var tenant = CurrentUser.get().tenantId();
        return respond(retryOnRace(() -> ingest.placeOrder(tenant, key, order)));
    }

    @PostMapping("/orders/{orderRef}/cancel")
    public OrderView cancel(@PathVariable String orderRef, @Valid @RequestBody CancelRequest request) {
        return ingest.cancelOrder(CurrentUser.get().tenantId(), orderRef, request.cancelledAt());
    }

    @PostMapping("/trades")
    public ResponseEntity<TradeView> recordTrade(@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 100) String key,
            @Valid @RequestBody TradeRequest trade) {
        var tenant = CurrentUser.get().tenantId();
        return respond(retryOnRace(() -> ingest.recordTrade(tenant, key, trade)));
    }

    /**
     * Two identical requests at the same instant can both pass the "already stored?" check; the
     * database's UNIQUE constraint then rejects the second. Retrying once finds the stored row and
     * returns it as a replay, so the client sees one consistent answer.
     */
    private static <T> IngestResult<T> retryOnRace(Supplier<IngestResult<T>> call) {
        try {
            return call.get();
        } catch (DataIntegrityViolationException race) {
            return call.get();
        }
    }

    private static <T> ResponseEntity<T> respond(IngestResult<T> result) {
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .header("Idempotent-Replayed", String.valueOf(result.replayed()))
                .body(result.data());
    }
}
