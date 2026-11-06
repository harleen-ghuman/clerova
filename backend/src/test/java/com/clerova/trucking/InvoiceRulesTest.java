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
                .isEqualTo("Fresno to Phoenix - \"dock 4\" ");
        assertThat(InvoicePdf.safe("Se\u00F1or Freight \u2728 LLC")).isEqualTo("Se\u00F1or Freight LLC");
        assertThat(InvoicePdf.safe("Total  $5")).isEqualTo("Total  $5");
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

    @Test
    void dueDatesCrossMonthEndsAndLeapYears() {
        Invoice invoice = new Invoice();
        invoice.setSendTo(Invoice.SendTo.BROKER);
        Load load = new Load();
        load.setPaymentTerms("Net 30");

        assertThat(InvoiceService.dueDate(invoice, load, java.time.LocalDate.of(2027, 1, 31)))
                .isEqualTo(java.time.LocalDate.of(2027, 3, 2));
        assertThat(InvoiceService.dueDate(invoice, load, java.time.LocalDate.of(2028, 1, 30)))
                .isEqualTo(java.time.LocalDate.of(2028, 2, 29));
        assertThat(InvoiceService.dueDate(invoice, load, java.time.LocalDate.of(2026, 12, 15)))
                .isEqualTo(java.time.LocalDate.of(2027, 1, 14));

        // A factor pays on its own schedule, and quick pay has no due date
        invoice.setSendTo(Invoice.SendTo.FACTOR);
        assertThat(InvoiceService.dueDate(invoice, load, java.time.LocalDate.of(2027, 1, 31))).isNull();
    }

    @Test
    void theCarriersTimeZoneDecidesTheDayAndMonth() {
        // 12:30 AM on Nov 1 in New York is still Oct 31 in California
        Instant sent = Instant.parse("2026-11-01T04:30:00Z");
        assertThat(java.time.LocalDate.ofInstant(sent, InvoiceService.zoneOf("America/New_York")))
                .isEqualTo(java.time.LocalDate.of(2026, 11, 1));
        assertThat(java.time.LocalDate.ofInstant(sent, InvoiceService.zoneOf(null)))
                .isEqualTo(java.time.LocalDate.of(2026, 10, 31));
        assertThat(InvoiceService.zoneOf("Not/AZone")).isEqualTo(InvoiceService.DEFAULT_ZONE);
    }

    @Test
    void detentionPastMidnightAndOverTheClockChange() {
        Load load = new Load();
        load.setDetentionFreeMinutes(120);
        load.setDetentionRateCents(5000L);
        LoadStop stop = new LoadStop();

        // Arrived 9:41 PM, left 12:26 AM the next day: 2h 45m on site, 45 minutes billed
        Instant arrived = Instant.parse("2026-11-06T05:41:00Z");
        stop.setArrivedAt(arrived);
        stop.setLeftAt(Instant.parse("2026-11-06T08:26:00Z"));
        Detention.Result result = Detention.at(stop, load, Instant.now());
        assertThat(result.minutesOnSite()).isEqualTo(165);
        assertThat(result.billableMinutes()).isEqualTo(45);
        assertThat(result.amountCents()).isEqualTo(3750);

        // Clocks go back at 2 AM on Nov 1: 12:30 AM to 3:30 AM on the wall is 4 real hours
        stop.setArrivedAt(Instant.parse("2026-11-01T07:30:00Z"));
        stop.setLeftAt(Instant.parse("2026-11-01T11:30:00Z"));
        Detention.Result overChange = Detention.at(stop, load, Instant.now());
        assertThat(overChange.minutesOnSite()).isEqualTo(240);
        assertThat(overChange.amountCents()).isEqualTo(10000);
    }
}
