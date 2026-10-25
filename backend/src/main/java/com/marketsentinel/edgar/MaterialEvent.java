package com.marketsentinel.edgar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** An SEC Form 8-K: a company reporting a material event. `items` lists the 8-K item numbers. */
@Entity
@Table(name = "material_events")
public class MaterialEvent {

    /**
     * 8-K items that typically move a stock: 1.01 material agreement, 1.02 terminated agreement,
     * 2.01 acquisition or disposal, 2.02 results of operations, 5.01 change in control,
     * 5.02 officer or director changes, 8.01 other events.
     */
    public static final Set<String> PRICE_SENSITIVE_ITEMS = Set.of("1.01", "1.02", "2.01", "2.02", "5.01", "5.02",
            "8.01");

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String ticker;

    @Column(nullable = false)
    private String cik;

    @Column(nullable = false)
    private String accession;

    @Column(nullable = false)
    private String items;

    @Column(nullable = false)
    private LocalDate filedOn;

    protected MaterialEvent() {
    }

    public MaterialEvent(UUID tenantId, String ticker, String cik, String accession, String items,
            LocalDate filedOn) {
        this.tenantId = tenantId;
        this.ticker = ticker;
        this.cik = cik;
        this.accession = accession;
        this.items = items == null ? "" : items;
        this.filedOn = filedOn;
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getCik() { return cik; }
    public String getAccession() { return accession; }
    public String getItems() { return items; }
    public LocalDate getFiledOn() { return filedOn; }

    public List<String> itemList() {
        return items.isBlank() ? List.of() : Arrays.stream(items.split(",")).map(String::trim).toList();
    }

    public boolean isPriceSensitive() {
        return itemList().stream().anyMatch(PRICE_SENSITIVE_ITEMS::contains);
    }
}
