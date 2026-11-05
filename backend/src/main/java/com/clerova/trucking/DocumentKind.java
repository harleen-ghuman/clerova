package com.clerova.trucking;

import java.util.Locale;

/** What a load's document is; the invoice packet picks from these. */
public enum DocumentKind {
    RATE_CON, BOL, POD, LUMPER_RECEIPT, SCALE_TICKET, INVOICE, OTHER;

    /** The AI's answer, or OTHER when it isn't one of these. */
    public static DocumentKind parse(String value) {
        if (value == null || value.isBlank()) {
            return OTHER;
        }

        try {
            return valueOf(value.strip().toUpperCase(Locale.ROOT).replace(' ', '_'));
        } catch (IllegalArgumentException e) {
            return OTHER;
        }
    }
}
