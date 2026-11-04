package com.clerova.trucking;

import java.util.Optional;
import java.util.UUID;

/** A load id in the "+hash" of a reply-to address, so a broker's answer finds its load. */
public final class LoadMailboxHash {

    private static final String PREFIX = "ld-";

    private LoadMailboxHash() {
    }

    public static String of(UUID loadId) {
        return PREFIX + loadId.toString().replace("-", "");
    }

    public static Optional<UUID> parse(String mailboxHash) {
        if (mailboxHash == null || !mailboxHash.startsWith(PREFIX)) {
            return Optional.empty();
        }

        String hex = mailboxHash.substring(PREFIX.length());

        if (!hex.matches("[0-9a-fA-F]{32}")) {
            return Optional.empty();
        }

        return Optional.of(UUID.fromString(hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-" + hex.substring(20)));
    }
}
