package com.clerova.trucking;

/** What a broker's email is about. */
public enum ReplyKind {
    STATUS,
    APPOINTMENT,
    PAPERWORK,
    RATE,
    OTHER,
    /** Sent by the dispatcher from the detention panel (never chosen by the AI). */
    DETENTION,
    /** An invoice packet sent from Clerova (never chosen by the AI). */
    INVOICE
}
