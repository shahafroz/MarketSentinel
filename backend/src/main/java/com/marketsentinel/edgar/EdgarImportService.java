package com.marketsentinel.edgar;

import com.marketsentinel.audit.AuditService;
import com.marketsentinel.detection.DetectionService;
import com.marketsentinel.web.ApiException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.client.RestClientException;

/** Imports Form 4 insider transactions and 8-K events for a list of tickers, then runs the insider screen. */
@Service
public class EdgarImportService {

    private static final Logger log = LoggerFactory.getLogger(EdgarImportService.class);
    private static final int MAX_FORM4_PER_TICKER = 60;

    private final EdgarClient edgar;
    private final InsiderTransactionRepository insiders;
    private final MaterialEventRepository events;
    private final DetectionService detection;
    private final AuditService audit;
    private final TransactionTemplate tx;

    public EdgarImportService(EdgarClient edgar, InsiderTransactionRepository insiders, MaterialEventRepository events,
            DetectionService detection, AuditService audit, TransactionTemplate tx) {
        this.edgar = edgar;
        this.insiders = insiders;
        this.events = events;
        this.detection = detection;
        this.audit = audit;
        this.tx = tx;
    }

    public record TickerResult(String ticker, String company, int form4Filings, int insiderTransactions,
            int eightKs, String error) {
    }

    public record ImportResult(List<TickerResult> tickers, DetectionService.RunResult screen) {
    }

    /**
     * Network calls happen outside any database transaction (a slow SEC response must not hold a
     * database connection); each ticker's rows are then saved in one short transaction.
     */
    public ImportResult importTickers(UUID tenantId, String actor, List<String> tickers, int days) {
        if (tickers.isEmpty() || tickers.size() > 10) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Send between 1 and 10 tickers");
        }
        LocalDate since = LocalDate.now().minusDays(Math.max(1, Math.min(days, 730)));
        List<TickerResult> results = new ArrayList<>();
        for (String raw : tickers) {
            String ticker = raw.trim().toUpperCase();
            try {
                results.add(importOne(tenantId, ticker, since));
            } catch (RestClientException | IllegalArgumentException e) {
                log.warn("EDGAR import failed for {}: {}", ticker, e.getMessage());
                results.add(new TickerResult(ticker, null, 0, 0, 0, "SEC request failed: " + e.getMessage()));
            }
        }
        DetectionService.RunResult screen = detection.runInsiderScreen(tenantId);
        tx.executeWithoutResult(s -> audit.record(tenantId, actor, "EDGAR_IMPORTED", "edgar", String.join(",", tickers),
                results.stream().mapToInt(TickerResult::insiderTransactions).sum() + " insider transactions, "
                        + screen.newAlerts() + " new alerts"));
        return new ImportResult(results, screen);
    }

    private TickerResult importOne(UUID tenantId, String ticker, LocalDate since) {
        EdgarClient.Company company = edgar.company(ticker)
                .orElseThrow(() -> new IllegalArgumentException("Unknown ticker " + ticker));
        List<EdgarClient.Filing> filings = edgar.recentFilings(company.cik()).stream()
                .filter(f -> !LocalDate.parse(f.filingDate()).isBefore(since))
                .toList();

        List<MaterialEvent> newEvents = new ArrayList<>();
        filings.stream().filter(f -> f.form().equals("8-K"))
                .filter(f -> !events.existsByTenantIdAndAccession(tenantId, f.accession()))
                .forEach(f -> newEvents.add(new MaterialEvent(tenantId, ticker, company.cik(), f.accession(), f.items(),
                        LocalDate.parse(f.filingDate()))));

        List<InsiderTransaction> newTx = new ArrayList<>();
        List<EdgarClient.Filing> form4s = filings.stream().filter(f -> f.form().equals("4")).limit(MAX_FORM4_PER_TICKER)
                .toList();
        for (EdgarClient.Filing f : form4s) {
            if (insiders.existsByTenantIdAndAccessionAndLineNo(tenantId, f.accession(), 1)) {
                continue;
            }
            try {
                Form4Parser.Form4 form = Form4Parser.parse(edgar.form4Xml(company.cik(), f));
                for (Form4Parser.Line line : form.lines()) {
                    newTx.add(new InsiderTransaction(tenantId, ticker, company.cik(), f.accession(), line.lineNo(),
                            form.owner().name(), form.owner().title(), line.date(), line.code(), line.shares(), line.price(),
                            line.acquired(), LocalDate.parse(f.filingDate())));
                }
            } catch (RestClientException | IllegalArgumentException e) {
                log.debug("Skipping Form 4 {}: {}", f.accession(), e.getMessage());
            }
        }
        tx.executeWithoutResult(s -> {
            events.saveAll(newEvents);
            insiders.saveAll(newTx);
        });
        return new TickerResult(ticker, company.name(), form4s.size(), newTx.size(), newEvents.size(), null);
    }
}
