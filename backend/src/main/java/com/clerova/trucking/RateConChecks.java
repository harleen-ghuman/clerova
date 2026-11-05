package com.clerova.trucking;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Decides what to flag on a rate con before the carrier signs it. The AI
 * only reads the rate con; these rules are plain code, so every flag is
 * predictable and testable.
 */
final class RateConChecks {

    static final String UNREADABLE = "UNREADABLE";
    static final String RATE_MISMATCH = "RATE_MISMATCH";
    static final String NO_DETENTION = "NO_DETENTION";
    static final String DETENTION_CONDITION = "DETENTION_CONDITION";
    static final String PAPERWORK_FINE = "PAPERWORK_FINE";
    static final String QUICK_PAY_FEE = "QUICK_PAY_FEE";
    static final String MC_MISSING = "MC_MISSING";
    static final String BROKER_NOT_FOUND = "BROKER_NOT_FOUND";
    static final String BROKER_NOT_AUTHORIZED = "BROKER_NOT_AUTHORIZED";
    static final String BROKER_NAME_MISMATCH = "BROKER_NAME_MISMATCH";

    /** Differences smaller than a dollar are rounding, not a different rate. */
    private static final long RATE_TOLERANCE_CENTS = 100;

    /** Words that don't tell two company names apart. */
    private static final Set<String> NAME_NOISE = Set.of(
            "llc", "inc", "co", "corp", "corporation", "company", "ltd", "the", "and", "of", "lp", "llp");

    private RateConChecks() {
    }

    static List<LoadFlag> flags(RateConResult read, Load load, String mcNumber, BrokerCheck check) {
        List<LoadFlag> flags = new ArrayList<>();

        if (load.getRateCents() == null || load.getStops().isEmpty()) {
            flags.add(flag(UNREADABLE, FlagLevel.FIX,
                    "Couldn't read the rate or the stops",
                    "Check this rate con yourself before you sign it.",
                    null));
        }

        if (load.getRateCents() != null && load.getAgreedRateCents() != null
                && Math.abs(load.getRateCents() - load.getAgreedRateCents()) >= RATE_TOLERANCE_CENTS) {
            long difference = Math.abs(load.getRateCents() - load.getAgreedRateCents());
            boolean lower = load.getRateCents() < load.getAgreedRateCents();

            flags.add(flag(RATE_MISMATCH, FlagLevel.FIX,
                    "Rate is " + Money.dollars(difference) + (lower ? " lower" : " higher") + " than the email says",
                    "The email says " + Money.dollars(load.getAgreedRateCents())
                            + ". The rate con says " + Money.dollars(load.getRateCents())
                            + ". Ask for a corrected rate con before you sign.",
                    read.rateQuote));
        }

        boolean noDetentionTerms = load.getDetentionFreeMinutes() == null && load.getDetentionRateCents() == null;

        if (noDetentionTerms && hasText(read.detentionCondition)) {
            // It mentions detention but gives no rate: one flag, not two that read as opposites
            flags.add(flag(NO_DETENTION, FlagLevel.NOTE,
                    "Detention rate isn't on the rate con",
                    "It only says: " + read.detentionCondition.strip()
                            + " Ask the broker for the detention rate and free time before you sign.",
                    read.detentionQuote));
        } else if (noDetentionTerms) {
            flags.add(flag(NO_DETENTION, FlagLevel.NOTE,
                    "No detention pay on this rate con",
                    "If the driver waits at a dock, there's nothing to bill. Ask the broker to add detention terms before you sign.",
                    null));
        } else if (hasText(read.detentionCondition)) {
            flags.add(flag(DETENTION_CONDITION, FlagLevel.NOTE,
                    "Detention pay has a condition",
                    "To get paid for detention: " + read.detentionCondition.strip(),
                    read.detentionQuote));
        }

        Long fine = Money.cents(read.latePaperworkFine);

        if (fine != null) {
            flags.add(flag(PAPERWORK_FINE, FlagLevel.NOTE,
                    Money.dollars(fine) + " fine if paperwork is late",
                    hasText(read.paperworkDeadline)
                            ? "Paperwork deadline: " + read.paperworkDeadline.strip()
                            : "Send the signed BOL and POD as soon as the load delivers.",
                    read.paperworkQuote));
        }

        BigDecimal quickPay = Money.percent(read.quickPayFeePercent);

        if (quickPay != null) {
            String percent = quickPay.stripTrailingZeros().toPlainString() + "%";
            String cost = load.getRateCents() != null
                    ? " (" + Money.dollars(quickPay.multiply(BigDecimal.valueOf(load.getRateCents()))
                            .movePointLeft(2).longValue()) + " on this load)"
                    : "";

            flags.add(flag(QUICK_PAY_FEE, FlagLevel.NOTE,
                    "Quick pay costs " + percent,
                    (hasText(read.paymentTerms) ? "Payment terms: " + read.paymentTerms.strip() + ". " : "")
                            + "Quick pay takes " + percent + cost + ".",
                    read.paymentQuote));
        }

        if (mcNumber == null) {
            flags.add(flag(MC_MISSING, FlagLevel.FIX,
                    "No MC number for the broker",
                    "Clerova can't check this broker with FMCSA. Ask for their MC number before you sign.",
                    null));
        } else if (check != null) {
            String fmcsaName = check.legalName() != null ? check.legalName() : check.dbaName();

            switch (check.status()) {
                case NOT_FOUND -> flags.add(flag(BROKER_NOT_FOUND, FlagLevel.FIX,
                        "FMCSA has no company with MC " + mcNumber,
                        "Check the MC number before you sign. Fake brokers often use a wrong or borrowed number.",
                        null));
                case NOT_AUTHORIZED -> flags.add(flag(BROKER_NOT_AUTHORIZED, FlagLevel.FIX,
                        "MC " + mcNumber + " has no active broker authority",
                        "FMCSA lists " + (fmcsaName != null ? fmcsaName : "this MC number")
                                + " without active broker authority. Confirm who you're working with before you haul.",
                        null));
                case ACTIVE -> {
                    if (load.getBroker() != null && !sameCompany(load.getBroker().getName(), check)) {
                        flags.add(flag(BROKER_NAME_MISMATCH, FlagLevel.FIX,
                                "Broker name doesn't match FMCSA",
                                "The rate con says " + load.getBroker().getName() + ". FMCSA lists MC " + mcNumber
                                        + " as " + fmcsaName + ". Call the broker on the phone number FMCSA lists, not the one in the email.",
                                null));
                    }
                }
            }
        }

        return flags;
    }

    /** Whether a rate con's broker name is one of the names FMCSA lists for the MC number. */
    static boolean sameCompany(String name, BrokerCheck check) {
        String wanted = normalize(name);

        if (wanted.isEmpty()) {
            return true;
        }

        for (String listed : new String[] {check.legalName(), check.dbaName()}) {
            String other = normalize(listed);

            if (!other.isEmpty() && (other.contains(wanted) || wanted.contains(other))) {
                return true;
            }
        }

        // FMCSA had no name to compare
        return normalize(check.legalName()).isEmpty() && normalize(check.dbaName()).isEmpty();
    }

    static String normalize(String name) {
        if (name == null) {
            return "";
        }

        StringBuilder result = new StringBuilder();

        for (String word : name.toLowerCase(Locale.ROOT).replace("&", " and ").split("[^a-z0-9]+")) {
            if (!word.isEmpty() && !NAME_NOISE.contains(word)) {
                result.append(result.isEmpty() ? "" : " ").append(word);
            }
        }

        return result.toString();
    }

    private static LoadFlag flag(String code, FlagLevel level, String title, String detail, String source) {
        LoadFlag flag = new LoadFlag();
        flag.setCode(code);
        flag.setLevel(level);
        flag.setTitle(title);
        flag.setDetail(detail);
        flag.setSourceText(hasText(source) ? clip(source.strip(), 500) : null);
        return flag;
    }

    private static boolean hasText(String text) {
        return text != null && !text.isBlank();
    }

    private static String clip(String text, int max) {
        return text.length() <= max ? text : text.substring(0, max) + "…";
    }
}
