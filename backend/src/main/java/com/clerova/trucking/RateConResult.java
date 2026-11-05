package com.clerova.trucking;

import java.util.ArrayList;
import java.util.List;

/**
 * What the AI read from a broker's rate confirmation. Amounts are text
 * (digits, like "2150.00") or empty when the rate con doesn't say, so
 * nothing is guessed; the code turns them into numbers. Each quote is the
 * rate con's own words, shown next to the flag it explains.
 */
public class RateConResult {

    public boolean isRateConfirmation;

    /** What the document is, as a DocumentKind name (RATE_CON, BOL, POD...). */
    public String documentKind;
    /** Load, order, PO, BOL and pickup numbers printed on the document. */
    public List<String> referenceNumbers = new ArrayList<>();

    public String brokerName;
    public String brokerMcNumber;
    public String brokerEmail;
    public String brokerPhone;

    public String loadNumber;
    public String equipment;

    /** Total pay to the carrier for the load. */
    public String lineHaulRate;
    public String rateQuote;
    /** An all-in rate the email itself states (not the rate con), if any. */
    public String rateInEmail;

    public List<Stop> stops = new ArrayList<>();

    public String detentionFreeHours;
    public String detentionRatePerHour;
    /** A condition for getting paid, like notice within 30 minutes or written approval. */
    public String detentionCondition;
    public String detentionQuote;

    public String tonuAmount;

    public String paperworkDeadline;
    public String latePaperworkFine;
    public String paperworkQuote;

    public String paymentTerms;
    public String quickPayFeePercent;
    public String paymentQuote;

    public static class Stop {
        /** PICKUP or DELIVERY. */
        public String kind;
        public String place;
        public String appointment;
    }
}
