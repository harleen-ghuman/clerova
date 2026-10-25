package com.clerova.domain;

public enum InboundMessageStatus {
    /** Stored, waiting for background processing. */
    RECEIVED,
    PROCESSING,
    /** Became a new work item. */
    PROCESSED,
    /** Added to an existing work item's conversation. */
    ATTACHED,
    /** Not actionable (automated, newsletter, forwarding confirmation). */
    IGNORED,
    FAILED
}
