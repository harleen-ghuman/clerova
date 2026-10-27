package com.clerova.dto;

import jakarta.validation.constraints.Size;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * What the company tells Clerova so suggested replies can answer common
 * questions. Every field is optional plain text.
 */
public record CompanyInfo(
        @Size(max = MAX_FIELD, message = TOO_LONG) String rentAndPayments,
        @Size(max = MAX_FIELD, message = TOO_LONG) String officeAndContact,
        @Size(max = MAX_FIELD, message = TOO_LONG) String emergencies,
        @Size(max = MAX_FIELD, message = TOO_LONG) String maintenance,
        @Size(max = MAX_FIELD, message = TOO_LONG) String utilities,
        @Size(max = MAX_FIELD, message = TOO_LONG) String pets,
        @Size(max = MAX_FIELD, message = TOO_LONG) String parking,
        @Size(max = MAX_FIELD, message = TOO_LONG) String trashAndRecycling,
        @Size(max = MAX_FIELD, message = TOO_LONG) String noiseAndGuests,
        @Size(max = MAX_FIELD, message = TOO_LONG) String keysAndLockouts,
        @Size(max = MAX_FIELD, message = TOO_LONG) String moveInAndOut,
        @Size(max = MAX_FIELD, message = TOO_LONG) String renewals,
        @Size(max = MAX_FIELD, message = TOO_LONG) String other
) {

    public static final int MAX_FIELD = 1_500;
    public static final int MAX_TOTAL = 8_000;
    static final String TOO_LONG = "Keep each answer under 1,500 characters.";

    public static final CompanyInfo EMPTY =
            new CompanyInfo(null, null, null, null, null, null, null, null, null, null, null, null, null);

    /** Filled-in answers with their labels, in display order. */
    public Map<String, String> entries() {
        Map<String, String> entries = new LinkedHashMap<>();

        put(entries, "Rent and payments", rentAndPayments);
        put(entries, "Office hours and contact", officeAndContact);
        put(entries, "Emergencies and after hours", emergencies);
        put(entries, "Maintenance and repairs", maintenance);
        put(entries, "Utilities", utilities);
        put(entries, "Pets", pets);
        put(entries, "Parking", parking);
        put(entries, "Trash and recycling", trashAndRecycling);
        put(entries, "Noise, guests and quiet hours", noiseAndGuests);
        put(entries, "Keys and lockouts", keysAndLockouts);
        put(entries, "Moving in and out", moveInAndOut);
        put(entries, "Lease renewals", renewals);
        put(entries, "Other common questions", other);

        return entries;
    }

    /** The same answers with blanks as null and surrounding spaces trimmed. */
    public CompanyInfo cleaned() {
        return new CompanyInfo(
                clean(rentAndPayments), clean(officeAndContact), clean(emergencies), clean(maintenance),
                clean(utilities), clean(pets), clean(parking), clean(trashAndRecycling),
                clean(noiseAndGuests), clean(keysAndLockouts), clean(moveInAndOut), clean(renewals),
                clean(other)
        );
    }

    private static void put(Map<String, String> entries, String label, String value) {
        if (value != null && !value.isBlank()) {
            entries.put(label, value.trim());
        }
    }

    private static String clean(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
