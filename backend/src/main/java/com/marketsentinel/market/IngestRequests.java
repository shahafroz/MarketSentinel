package com.marketsentinel.market;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;

/** Request and response bodies for the ingest API. Validation rejects bad data before it is stored. */
public final class IngestRequests {

    private IngestRequests() {
    }

    private static final String SYMBOL = "^[A-Z][A-Z0-9.\\-]{0,9}$";

    public record AccountRequest(
            @NotBlank @Size(max = 64) String accountRef,
            @NotBlank @Size(max = 64) String ownerRef,
            @NotNull AccountType accountType) {
    }

    public record OrderRequest(
            @NotBlank @Size(max = 64) String orderRef,
            @NotBlank @Size(max = 64) String accountRef,
            @NotBlank @Pattern(regexp = SYMBOL, message = "must be an upper-case ticker") String symbol,
            @NotNull Side side,
            @Positive long quantity,
            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal limitPrice,
            @NotNull Instant placedAt) {
    }

    public record CancelRequest(@NotNull Instant cancelledAt) {
    }

    public record TradeRequest(
            @NotBlank @Size(max = 64) String tradeRef,
            @NotBlank @Pattern(regexp = SYMBOL, message = "must be an upper-case ticker") String symbol,
            @NotBlank @Size(max = 64) String buyAccountRef,
            @NotBlank @Size(max = 64) String sellAccountRef,
            @Size(max = 64) String buyOrderRef,
            @Size(max = 64) String sellOrderRef,
            @NotNull @DecimalMin(value = "0.0001") @Digits(integer = 15, fraction = 4) BigDecimal price,
            @Positive long quantity,
            @NotNull Instant executedAt) {
    }

    public record OrderView(String orderRef, String accountRef, String symbol, Side side, long quantity,
            BigDecimal limitPrice, OrderStatus status, long filledQuantity, Instant placedAt, Instant cancelledAt) {

        static OrderView of(MarketOrder o) {
            return new OrderView(o.getOrderRef(), o.getAccountRef(), o.getSymbol(), o.getSide(), o.getQuantity(),
                    o.getLimitPrice(), o.getStatus(), o.getFilledQuantity(), o.getPlacedAt(), o.getCancelledAt());
        }
    }

    public record TradeView(String tradeRef, String symbol, String buyAccountRef, String sellAccountRef,
            BigDecimal price, long quantity, Instant executedAt) {

        public static TradeView of(Trade t) {
            return new TradeView(t.getTradeRef(), t.getSymbol(), t.getBuyAccountRef(), t.getSellAccountRef(), t.getPrice(),
                    t.getQuantity(), t.getExecutedAt());
        }
    }

    /** `replayed` = true when the Idempotency-Key was seen before and nothing new was stored. */
    public record IngestResult<T>(T data, boolean replayed) {
    }
}
