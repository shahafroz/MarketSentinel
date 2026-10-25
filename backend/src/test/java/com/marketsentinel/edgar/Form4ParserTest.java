package com.marketsentinel.edgar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class Form4ParserTest {

    private static final String FORM4 = """
            <?xml version="1.0"?>
            <ownershipDocument>
              <issuer><issuerCik>0000000001</issuerCik><issuerTradingSymbol>ACME</issuerTradingSymbol></issuer>
              <reportingOwner>
                <reportingOwnerId><rptOwnerName>Doe Jane</rptOwnerName></reportingOwnerId>
                <reportingOwnerRelationship>
                  <isDirector>1</isDirector><isOfficer>1</isOfficer><officerTitle>Chief Executive Officer</officerTitle>
                </reportingOwnerRelationship>
              </reportingOwner>
              <nonDerivativeTable>
                <nonDerivativeTransaction>
                  <transactionDate><value>2026-03-02</value></transactionDate>
                  <transactionCoding><transactionCode>P</transactionCode></transactionCoding>
                  <transactionAmounts>
                    <transactionShares><value>20000</value></transactionShares>
                    <transactionPricePerShare><value>15.25</value></transactionPricePerShare>
                    <transactionAcquiredDisposedCode><value>A</value></transactionAcquiredDisposedCode>
                  </transactionAmounts>
                </nonDerivativeTransaction>
                <nonDerivativeTransaction>
                  <transactionDate><value>2026-03-03-05:00</value></transactionDate>
                  <transactionCoding><transactionCode>S</transactionCode></transactionCoding>
                  <transactionAmounts>
                    <transactionShares><value>500</value></transactionShares>
                    <transactionPricePerShare><footnoteId id="F1"/></transactionPricePerShare>
                    <transactionAcquiredDisposedCode><value>D</value></transactionAcquiredDisposedCode>
                  </transactionAmounts>
                </nonDerivativeTransaction>
              </nonDerivativeTable>
            </ownershipDocument>
            """;

    @Test
    void readsOwnerAndTransactions() {
        Form4Parser.Form4 form = Form4Parser.parse(FORM4);

        assertThat(form.owner().name()).isEqualTo("Doe Jane");
        assertThat(form.owner().title()).isEqualTo("Chief Executive Officer, Director");
        assertThat(form.lines()).hasSize(2);
        assertThat(form.lines().get(0)).isEqualTo(new Form4Parser.Line(1, LocalDate.of(2026, 3, 2), "P",
                new BigDecimal("20000"), new BigDecimal("15.25"), true));
        assertThat(form.lines().get(1).date()).isEqualTo(LocalDate.of(2026, 3, 3));
        assertThat(form.lines().get(1).price()).isNull();
        assertThat(form.lines().get(1).acquired()).isFalse();
    }

    @Test
    void refusesExternalEntities() {
        String xxe = """
                <?xml version="1.0"?>
                <!DOCTYPE foo [ <!ENTITY xxe SYSTEM "file:///etc/passwd"> ]>
                <ownershipDocument><reportingOwner><rptOwnerName>&xxe;</rptOwnerName></reportingOwner></ownershipDocument>
                """;
        assertThatThrownBy(() -> Form4Parser.parse(xxe)).isInstanceOf(IllegalArgumentException.class);
    }
}
