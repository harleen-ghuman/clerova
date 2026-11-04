package com.clerova.trucking;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.NumberFormat;
import java.util.Locale;

/** Reads amounts the AI wrote as text, and shows cents as dollars. */
final class Money {

    /** No single load or charge is anywhere near this. */
    private static final long MAX_CENTS = 100_000_000L;

    private Money() {
    }

    /** "2,150.00" or "$2150" to cents; null when blank, unreadable or not positive. */
    static Long cents(String amount) {
        BigDecimal value = number(amount);

        if (value == null || value.signum() <= 0) {
            return null;
        }

        long cents = value.movePointRight(2).setScale(0, RoundingMode.HALF_UP).longValueExact();
        return cents > MAX_CENTS ? null : cents;
    }

    /** "2" or "2.5" hours to minutes; null when blank or unreadable. */
    static Integer minutesFromHours(String hours) {
        BigDecimal value = number(hours);

        if (value == null || value.signum() < 0 || value.compareTo(BigDecimal.valueOf(72)) > 0) {
            return null;
        }

        return value.multiply(BigDecimal.valueOf(60)).setScale(0, RoundingMode.HALF_UP).intValueExact();
    }

    /** "5" or "5%" to 5.0; null when blank, unreadable or not between 0 and 100. */
    static BigDecimal percent(String percent) {
        BigDecimal value = number(percent);
        return value == null || value.signum() <= 0 || value.compareTo(BigDecimal.valueOf(100)) > 0 ? null : value;
    }

    static String dollars(long cents) {
        NumberFormat format = NumberFormat.getCurrencyInstance(Locale.US);
        return format.format(BigDecimal.valueOf(cents, 2));
    }

    private static BigDecimal number(String text) {
        if (text == null) {
            return null;
        }

        String cleaned = text.replaceAll("[$,%\\s]|USD", "");

        if (cleaned.isEmpty() || !cleaned.matches("\\d+(\\.\\d+)?")) {
            return null;
        }

        try {
            return new BigDecimal(cleaned);
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
