package com.clerova.trucking;

import java.util.List;

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

    /** A signed, scanned rate con whose text layer came out garbled (names and numbers made up). */
    private static final String GARBLED_SCAN = """
            FOR LOAD INFORMATION: Carrier Rate Confirmation
            NORTHPASS load #5520194-1
            LOGISTICS OROUP Dana Rul.:.
            Phone: 530-555-01◄2 Rate: $1,212.00 USD
            Email: druiz@northpass.example
            SIERRA RUN LLC MC: 1048817 I DOT: 0329911 INVOICIINSTRUCTIONS
            QulcllPay vii TrtumpflP,y llt ,11111t 2.5%.. Haw your tund9 wllflln two_.,,,_ dep.
            Equlipmlft VS3,53'\\Wl M lf1VOlcfnl .ubmltted WM emlll Ind mall wtl,,. paid In fl«
            3 t-dlly term,,
            Adv-: EFS checks wt11 be charged the larger of $5 or 4%.
            RATE DETAILS
            Rate per Unit (50 per net ton) USD $50.00 • 24.24 = $1,212.00
            Total: USD $1,212.00
            NorthPass Logistics MC:771204
            STOPS
            4/17/2026 Pine Flat Ranch 8-3 FCFS
            Plcbp HC 12 Box 410
            Ely,HV,89301
            lnstrvctlons: Driver « Disf)Moh must notfy Dana O 530-555-0172 when the truck is loaded and unloaded. failur
            notficatlon wil reail In a $25 fee.
            Note: Detention pay is not appblble If the broker Is not notified °'
            POD~ be turned in by the f<llowing Tuesday eftw ~. I f POO !$late~ wi1 be a $25 Fee for each~ it~
            4/18/2026 Valley Oak Ranch 7--4 FCFS
            Dropoff 8749 Lone Oak Ave
            Hanford, CA, 93230
            """;

    @Test
    void doesNotGuessAtGarbledValues() {
        RateConResult read = truckingAiService.readRateCon("Subject: Fwd:\n\n", GARBLED_SCAN);

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(read.loadNumber).contains("5520194");
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(121200L);
        assertThat(TruckingIntakeService.mcNumber(read.brokerMcNumber)).isEqualTo("771204");
        // "net 3 t-day terms" is cut off: never "3" or "Net 3"
        assertThat(read.paymentTerms == null ? "" : read.paymentTerms.strip())
                .doesNotMatch("(?i)(net\\s*)?3(\\s*days?)?");
        assertThat(Money.cents(read.detentionRatePerHour)).isNull();
        // Quick pay is 2.5%; the 4% is the fuel advance (EFS) fee
        assertThat(read.quickPayFeePercent).isIn("2.5", "2.50");
        // The $25 notification fee isn't a detention rule
        assertThat(read.detentionCondition == null ? "" : read.detentionCondition).doesNotContain("$25");
    }

    /** Draws text as a slightly crooked gray "scan", like a phone photo of paper. */
    private static List<String> scan(String text) throws Exception {
        String[] lines = text.strip().split("\n");
        java.awt.image.BufferedImage image = new java.awt.image.BufferedImage(1275, 1650, java.awt.image.BufferedImage.TYPE_INT_RGB);
        java.awt.Graphics2D g = image.createGraphics();
        g.setColor(new java.awt.Color(238, 236, 230));
        g.fillRect(0, 0, image.getWidth(), image.getHeight());
        g.rotate(Math.toRadians(0.8), 600, 800);
        g.setRenderingHint(java.awt.RenderingHints.KEY_TEXT_ANTIALIASING, java.awt.RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new java.awt.Color(40, 40, 45));
        g.setFont(new java.awt.Font(java.awt.Font.MONOSPACED, java.awt.Font.PLAIN, 19));
        int y = 90;
        for (String line : lines) {
            g.drawString(line, 70, y);
            y += 27;
        }
        g.dispose();
        java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream();
        javax.imageio.ImageIO.write(image, "png", out);
        return DocumentImages.dataUrls(out.toByteArray(), "scan.png", "image/png");
    }

    @Test
    void readsAScannedRateConFromItsPicture() throws Exception {
        // No text layer at all: only the picture
        RateConResult read = truckingAiService.readRateCon("Subject: Fwd:\n\n", "", scan(DRY_VAN));

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(DocumentKind.parse(read.documentKind)).isEqualTo(DocumentKind.RATE_CON);
        assertThat(read.loadNumber).contains("88213");
        assertThat(TruckingIntakeService.mcNumber(read.brokerMcNumber)).isEqualTo("845210");
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(215000L);
        assertThat(read.stops).hasSize(2);
        assertThat(read.stops.get(0).place).contains("Fresno");
        assertThat(Money.cents(read.detentionRatePerHour)).isEqualTo(5000L);
    }

    @Test
    void readsABolAndTheNumbersOnIt() throws Exception {
        String bol = """
                BILL OF LADING                         Date: 04/17/2026
                Shipper: Pine Flat Ranch, HC 12 Box 410, Ely, NV 89301
                Consignee: Valley Oak Ranch, 8749 Lone Oak Ave, Hanford, CA 93230
                Carrier: Sierra Run LLC        Trailer #: V5327
                Broker load #: 5520194-1       PO #: 7781-A
                Product: Alfalfa hay, 24 tons, 32 bales
                Shipper signature: ______      Driver signature: ______
                """;
        RateConResult read = truckingAiService.readRateCon("Subject: Ely to Hanford\n\n", "", scan(bol));

        assertThat(read.isRateConfirmation).isFalse();
        assertThat(DocumentKind.parse(read.documentKind)).isEqualTo(DocumentKind.BOL);
        assertThat(String.join(" ", read.referenceNumbers)).contains("5520194");
    }

    /** A TMS-style rate con: the only MC printed is the carrier's, in the truck and driver row. */
    private static final String CARRIER_MC_ONLY = """
            Shipment # NWFS20418 Rate Confirmation
            FROM                                   DATE        TIME
            Lena Ortiz                             05/12/2026  20:00
            lortiz@northwind.example
            Northwind Freight Solutions            TO: SIERRA RUN LLC   ATT: Sam Ortiz
            2210 Harbor Rd, Fargo, ND 58102            PHONE (559) 555-0148
            MC #      DOT #     TRUCK #  TRAILER #  DRIVER   DRIVER CELL
            1048817   3299110   212      V7714      Mario    (559) 555-0177
            SIZE & TYPE   DESCRIPTION   TOTAL WEIGHT   MILES
            Van 53 FT     FAK           35,000 LB      186
            Pickup Location (Stop # 1)
            Name: Northwind Freight - RNO   Address: 985 Glendale Avenue, SPARKS, NV 89431
            Appt Date/Time: 05/12/2026 20:00
            Drop Location (Stop # 2)
            Name: DDP   Address: 2800 S El Dorado ST, STOCKTON, CA 95206
            Appt Date/Time: 05/13/2026 00:30
            CHARGES
            Freight Charge $500.00
            TOTAL RATE $500.00 US Dollars
            PLEASE EMAIL A COPY OF THE RATE CONFIRMATION, POD & INVOICE TO: ap@northwind.example
            """;

    @Test
    void neverTakesTheCarriersMcForTheBrokers() {
        RateConResult read = truckingAiService.readRateCon(
                "Subject: Rate Confirmation for Shipment NWFS20418\n\n", CARRIER_MC_ONLY);

        assertThat(read.isRateConfirmation).isTrue();
        assertThat(read.brokerName).containsIgnoringCase("Northwind");
        assertThat(TruckingIntakeService.brokerMc(read)).isNull();
        assertThat(Money.cents(read.lineHaulRate)).isEqualTo(50000L);
    }

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
