package com.clerova.email;

import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * The original sender of an email someone forwarded by hand ("Fwd:"), read
 * from the forwarded header block Gmail, Outlook and Apple Mail add. Without
 * this, the request would show the forwarder as the sender and the reply
 * would go back to them instead of the resident.
 */
record ForwardedEmail(String fromEmail, String fromName, String subject) {

    private static final Pattern FORWARD_SUBJECT =
            Pattern.compile("^\\s*(?:(?:fwd?|fw)\\s*:\\s*)+", Pattern.CASE_INSENSITIVE);

    private static final Pattern FROM_LINE =
            Pattern.compile("^[\\s>*]*From:\\**\\s*(.+)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern SUBJECT_LINE =
            Pattern.compile("^[\\s>*]*Subject:\\**\\s*(.*)$", Pattern.CASE_INSENSITIVE | Pattern.MULTILINE);

    private static final Pattern EMAIL =
            Pattern.compile("[A-Za-z0-9._%+'-]+@[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+");

    static Optional<ForwardedEmail> detect(String subject, String body, String forwarderEmail) {
        if (subject == null || body == null || !FORWARD_SUBJECT.matcher(subject).find()) {
            return Optional.empty();
        }

        Matcher from = FROM_LINE.matcher(body);

        if (!from.find()) {
            return Optional.empty();
        }

        String line = from.group(1).trim();
        Matcher email = EMAIL.matcher(line);

        if (!email.find()) {
            return Optional.empty();
        }

        String address = email.group();

        if (address.equalsIgnoreCase(forwarderEmail)) {
            return Optional.empty();
        }

        // "Tina Alvarez <tina@x.com>" or "Tina Alvarez [mailto:tina@x.com]"
        String name = line.substring(0, email.start())
                .replaceAll("[<\\[(]|mailto:", "")
                .replace("\"", "")
                .trim();

        Matcher originalSubject = SUBJECT_LINE.matcher(body);
        String cleanSubject = originalSubject.find(from.end())
                ? originalSubject.group(1).trim()
                : FORWARD_SUBJECT.matcher(subject).replaceFirst("").trim();

        return Optional.of(new ForwardedEmail(
                address,
                name.isEmpty() || name.equalsIgnoreCase(address) ? null : name,
                cleanSubject.isEmpty() ? null : cleanSubject
        ));
    }
}
