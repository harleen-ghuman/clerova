package com.clerova.email;

import java.util.Optional;
import java.util.UUID;

/**
 * Encodes a work item id in the "+hash" part of a reply-to address so a
 * reply can be matched to its work item even when mail clients drop the
 * threading headers.
 */
public final class WorkItemMailboxHash {

    private static final String PREFIX = "wi-";

    private WorkItemMailboxHash() {
    }

    public static String of(UUID workItemId) {
        return PREFIX + workItemId.toString().replace("-", "");
    }

    public static Optional<UUID> parse(String mailboxHash) {
        if (mailboxHash == null || !mailboxHash.startsWith(PREFIX)) {
            return Optional.empty();
        }

        String hex = mailboxHash.substring(PREFIX.length());

        if (!hex.matches("[0-9a-fA-F]{32}")) {
            return Optional.empty();
        }

        return Optional.of(UUID.fromString(
                hex.substring(0, 8) + "-" + hex.substring(8, 12) + "-"
                        + hex.substring(12, 16) + "-" + hex.substring(16, 20) + "-"
                        + hex.substring(20)
        ));
    }
}
