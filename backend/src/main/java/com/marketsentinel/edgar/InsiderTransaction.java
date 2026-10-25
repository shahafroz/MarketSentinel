package com.marketsentinel.edgar;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** One non-derivative transaction line from an SEC Form 4 filing. */
@Entity
@Table(name = "insider_transactions")
public class InsiderTransaction {

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
    private int lineNo;

    @Column(nullable = false)
    private String ownerName;

    @Column(nullable = false)
    private String ownerTitle;

    @Column(nullable = false)
    private LocalDate transactionDate;

    /** SEC transaction code: P = open-market purchase, S = open-market sale, A = grant, ... */
    @Column(nullable = false)
    private String code;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal shares;

    @Column(precision = 19, scale = 4)
    private BigDecimal price;

    @Column(nullable = false)
    private boolean acquired;

    @Column(nullable = false)
    private LocalDate filedOn;

    protected InsiderTransaction() {
    }

    public InsiderTransaction(UUID tenantId, String ticker, String cik, String accession, int lineNo,
            String ownerName, String ownerTitle, LocalDate transactionDate, String code, BigDecimal shares,
            BigDecimal price, boolean acquired, LocalDate filedOn) {
        this.tenantId = tenantId;
        this.ticker = ticker;
        this.cik = cik;
        this.accession = accession;
        this.lineNo = lineNo;
        this.ownerName = ownerName;
        this.ownerTitle = ownerTitle == null ? "" : ownerTitle;
        this.transactionDate = transactionDate;
        this.code = code;
        this.shares = shares;
        this.price = price;
        this.acquired = acquired;
        this.filedOn = filedOn;
    }

    public Long getId() { return id; }
    public String getTicker() { return ticker; }
    public String getCik() { return cik; }
    public String getAccession() { return accession; }
    public String getOwnerName() { return ownerName; }
    public String getOwnerTitle() { return ownerTitle; }
    public LocalDate getTransactionDate() { return transactionDate; }
    public String getCode() { return code; }
    public BigDecimal getShares() { return shares; }
    public BigDecimal getPrice() { return price; }
    public boolean isAcquired() { return acquired; }
    public LocalDate getFiledOn() { return filedOn; }

    /** An open-market purchase with the insider's own money: the transaction type that matters here. */
    public boolean isOpenMarketPurchase() {
        return "P".equals(code) && acquired;
    }
}
