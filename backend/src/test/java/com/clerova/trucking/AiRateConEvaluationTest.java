package com.clerova.trucking;

import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The real AI on made-up rate cons laid out like real ones. Replace and
 * extend these with real rate cons from pilot carriers (names removed).
 */
@SpringBootTest
@Tag("ai-evaluation")
class AiRateConEvaluationTest {

    @Autowired
    private TruckingAiService truckingAiService;

    private static final String DRY_VAN = """
            CRESTLINE FREIGHT LLC            MC# 845210   DOT# 2948811
            4100 Commerce Blvd, Sacramento CA 95815   Ph (916) 555-0182   dispatch@crestline.example

            CARRIER RATE AND LOAD CONFIRMATION                 Load # 88213
            Carrier: SIERRA RUN TRANSPORT  MC# 1048817          Date: 11/03/2026
            Equipment: 53' Dry Van   Weight: 38,500 lbs   Commodity: Canned goods

            STOP 1 - PICKUP     11/04/2026 08:00 - 10:00
            Sunridge Packing, 1200 S Elm Ave, Fresno, CA 93706    PU# 55102
            STOP 2 - DELIVERY   11/05/2026 07:00 APPT
            Desert Foods DC, 4410 W Buckeye Rd, Phoenix, AZ 85043   PO# 7781-A

            RATE
            Line haul ............................ $1,850.00
            Fuel surcharge ....................... $300.00
            TOTAL CARRIER PAY .................... $2,150.00

            TERMS AND CONDITIONS
            1. Detention: $50.00/hr after 2 hours free at shipper or receiver. Carrier must notify
               broker within 30 minutes of arrival or detention will not be paid.
            2. Signed BOL/POD must be received within 24 hours of delivery. $100 deduction for POD
               received after 24 hours.
            3. Payment terms: Net 30 upon receipt of invoice and POD. QuickPay available: 2 days, 3% fee.
            4. Double brokering is strictly prohibited.
            """;

    private static final String REEFER_TWO_PICKUPS = """
            NORTHGATE LOGISTICS INC. - LOAD TENDER / RATE CONFIRMATION
            Broker MC 1610032 | ap@northgate.example | 702-555-0144
            Order: NG-2026-11-0457
            Trailer: Reefer 53' set at 34F continuous

            Pickup 1: Lodi Winery Supply, 401 E Turner Rd, Lodi CA - Nov 5 0900
            Pickup 2: Delta Cold Storage, 2200 Port Rd, Stockton CA - Nov 5 1300
            Drop: Silver State Beverage, 6100 S Polaris Ave, Las Vegas NV - Nov 6 0800 FCFS

            Agreed rate (all in): USD 3,400.00
            Detention $40 per hour starting after 2 hours. Detention requires written approval from the
            Northgate rep while the driver is on site.
            TONU: $150.
            Payment: Net 45. QuickPay 2 days at 5%.
            """;

    private static final String NO_DETENTION_NO_MC = """
            Valley Produce Brokers
            RATE CONFIRMATION #VPB-3301
            Pick: Turlock, CA 95380 (Fosters Cold) 11/07 6AM
            Deliver: Reno, NV 89502 (Truckee Market) 11/07 2PM
            Rate $1,100 flat.
            Terms: Net 30.
            """;

    private static final String INVOICE = """
            SIERRA RUN TRANSPORT
            INVOICE 1042                                  Date: 09/28/2026
            Bill to: Pinewood Logistics, billing@pinewood.example
            Load 77120  Manteca, CA to Sacramento, CA
            Line haul $650.00
            Total due: $650.00   Terms: Net 30
            """;

    @Test
    void readsADryVanRateCon() {
        RateConResult read = truckingAiService.readRateCon(
                "Subject: Rate Confirmation 88213\n\nHi, confirming 88213 Fresno to Phoenix, $2,250 all in. Rate con attached.",
                DRY_VAN);

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(read.brokerName).containsIgnoringCase("Crestline");
        assertThat(TruckingIntakeService.mcNumber(read.brokerMcNumber)).isEqualTo("845210");
        assertThat(read.loadNumber).contains("88213");
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(215000L);
        assertThat(Money.cents(read.rateInEmail)).isEqualTo(225000L);
        assertThat(read.stops).hasSize(2);
        assertThat(read.stops.getFirst().kind).isEqualToIgnoringCase("PICKUP");
        assertThat(read.stops.getFirst().place).contains("Fresno");
        assertThat(read.stops.get(1).kind).isEqualToIgnoringCase("DELIVERY");
        assertThat(Money.minutesFromHours(read.detentionFreeHours)).isEqualTo(120);
        assertThat(Money.cents(read.detentionRatePerHour)).isEqualTo(5000L);
        assertThat(read.detentionCondition).containsIgnoringCase("30 min");
        assertThat(Money.cents(read.latePaperworkFine)).isEqualTo(10000L);
        assertThat(Money.percent(read.quickPayFeePercent)).isEqualByComparingTo("3");
    }

    @Test
    void readsAMultiStopReeferRateCon() {
        RateConResult read = truckingAiService.readRateCon("Subject: Load tender NG-2026-11-0457\n\nPlease sign.", REEFER_TWO_PICKUPS);

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(TruckingIntakeService.mcNumber(read.brokerMcNumber)).isEqualTo("1610032");
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(340000L);
        assertThat(read.rateInEmail == null || Money.cents(read.rateInEmail) == null).isTrue();
        assertThat(read.stops).hasSize(3);
        assertThat(read.stops.get(1).place).contains("Stockton");
        assertThat(read.stops.get(2).kind).isEqualToIgnoringCase("DELIVERY");
        assertThat(Money.cents(read.detentionRatePerHour)).isEqualTo(4000L);
        assertThat(read.detentionCondition).containsIgnoringCase("written");
        assertThat(Money.cents(read.tonuAmount)).isEqualTo(15000L);
        assertThat(Money.percent(read.quickPayFeePercent)).isEqualByComparingTo("5");
    }

    @Test
    void leavesOutWhatTheRateConDoesntSay() {
        RateConResult read = truckingAiService.readRateCon("Subject: RC VPB-3301", NO_DETENTION_NO_MC);

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(110000L);
        assertThat(TruckingIntakeService.mcNumber(read.brokerMcNumber)).isNull();
        assertThat(Money.minutesFromHours(read.detentionFreeHours)).isNull();
        assertThat(Money.cents(read.detentionRatePerHour)).isNull();
        assertThat(Money.cents(read.latePaperworkFine)).isNull();
        assertThat(Money.cents(read.tonuAmount)).isNull();
    }

    @Test
    void anInvoiceIsNotARateCon() {
        RateConResult read = truckingAiService.readRateCon("Subject: Invoice 1042", INVOICE);
        assertThat(read.isRateConfirmation).isFalse();
    }
}
