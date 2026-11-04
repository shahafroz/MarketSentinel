package com.marketsentinel.edgar;

import com.marketsentinel.security.CurrentUser;
import com.marketsentinel.web.DecimalString;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/edgar")
public class EdgarController {

    private final EdgarImportService importer;
    private final InsiderTransactionRepository insiders;
    private final MaterialEventRepository events;

    public EdgarController(EdgarImportService importer, InsiderTransactionRepository insiders,
            MaterialEventRepository events) {
        this.importer = importer;
        this.insiders = insiders;
        this.events = events;
    }

    public record ImportRequest(@NotEmpty @Size(max = 10) List<String> tickers, @Min(1) @Max(730) Integer days) {
    }

    public record InsiderRow(String ticker, String owner, String title, LocalDate date, String code, @DecimalString BigDecimal shares,
            @DecimalString BigDecimal price, boolean acquired, String accession) {
    }

    public record EventRow(String ticker, LocalDate filedOn, String items, boolean priceSensitive, String accession) {
    }

    @PostMapping("/import")
    @PreAuthorize("hasAnyRole('ADMIN', 'SUPERVISOR', 'ANALYST')")
    public EdgarImportService.ImportResult importTickers(@Valid @RequestBody ImportRequest request) {
        CurrentUser me = CurrentUser.get();
        return importer.importTickers(me.tenantId(), me.actor(), request.tickers(), request.days() == null ? 180 : request.days());
    }

    @GetMapping("/insider-transactions")
    @Transactional(readOnly = true)
    public List<InsiderRow> insiderTransactions(@RequestParam(required = false) String ticker) {
        var tenant = CurrentUser.get().tenantId();
        var rows = ticker == null ? insiders.findByTenantIdOrderByTransactionDateDesc(tenant)
                : insiders.findByTenantIdAndTickerOrderByTransactionDateDesc(tenant, ticker.toUpperCase());
        return rows.stream().limit(500).map(t -> new InsiderRow(t.getTicker(), t.getOwnerName(), t.getOwnerTitle(),
                t.getTransactionDate(), t.getCode(), t.getShares(), t.getPrice(), t.isAcquired(), t.getAccession())).toList();
    }

    @GetMapping("/material-events")
    @Transactional(readOnly = true)
    public List<EventRow> materialEvents(@RequestParam(required = false) String ticker) {
        var tenant = CurrentUser.get().tenantId();
        var rows = ticker == null ? events.findByTenantIdOrderByFiledOnDesc(tenant)
                : events.findByTenantIdAndTickerOrderByFiledOnDesc(tenant, ticker.toUpperCase());
        return rows.stream().limit(500).map(e -> new EventRow(e.getTicker(), e.getFiledOn(), e.getItems(),
                e.isPriceSensitive(), e.getAccession())).toList();
    }
}
