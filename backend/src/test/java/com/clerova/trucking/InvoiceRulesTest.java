package com.clerova.trucking;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

class InvoiceRulesTest {

    @Test
    void paymentTermsGiveTheDays() {
        assertThat(InvoiceService.netDays("Net 30")).isEqualTo(30);
        assertThat(InvoiceService.netDays("NET-31 days from receipt of paperwork")).isEqualTo(31);
        assertThat(InvoiceService.netDays("Paid in 15 business days")).isEqualTo(15);
        assertThat(InvoiceService.netDays("net 3 t-day terms")).isEqualTo(3);
        assertThat(InvoiceService.netDays("Quick pay available")).isNull();
        assertThat(InvoiceService.netDays(null)).isNull();
        assertThat(InvoiceService.netDays("Net 900")).isNull();
    }

    @Test
    void quickPayFeeRoundsToTheCent() {
        assertThat(InvoiceService.quickPayFee(105750, new BigDecimal("2.5"))).isEqualTo(2644);
        assertThat(InvoiceService.quickPayFee(100000, new BigDecimal("3"))).isEqualTo(3000);
    }

    @Test
    void invoiceTextKeepsWhatTheFontCanDraw() {
        assertThat(InvoicePdf.safe("Fresno → Phoenix – “dock 4” 🚚"))
                .isEqualTo("Fresno  to  Phoenix - \"dock 4\" ");
        assertThat(InvoicePdf.safe("Café Line 1\nLine 2")).isEqualTo("Café Line 1\nLine 2");
    }

    @Test
    void detentionCountsWholeQuarterHoursPastTheFreeTimeUpToTheCap() {
        Load load = new Load();
        load.setDetentionFreeMinutes(120);
        load.setDetentionRateCents(5000L);
        LoadStop stop = new LoadStop();
        Instant arrived = Instant.parse("2026-11-05T14:00:00Z");
        stop.setArrivedAt(arrived);
        stop.setLeftAt(arrived.plus(194, ChronoUnit.MINUTES));

        Detention.Result result = Detention.at(stop, load, Instant.now());
        assertThat(result.billableMinutes()).isEqualTo(60);
        assertThat(result.amountCents()).isEqualTo(5000);
        assertThat(result.freeEndsAt()).isEqualTo(arrived.plus(2, ChronoUnit.HOURS));

        load.setDetentionMaxCents(3000L);
        assertThat(Detention.at(stop, load, Instant.now()).amountCents()).isEqualTo(3000);

        // No free time on the rate con: 2 hours
        load.setDetentionFreeMinutes(null);
        assertThat(Detention.at(stop, load, Instant.now()).freeMinutes()).isEqualTo(120);

        stop.setArrivedAt(null);
        assertThat(Detention.at(stop, load, Instant.now())).isNull();
    }
}
