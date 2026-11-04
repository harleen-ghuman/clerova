package com.clerova.trucking;

/** What FMCSA's public data says about a broker's MC number. */
public enum FmcsaStatus {
    /** Broker authority is active. */
    ACTIVE,
    /** The MC number exists but has no active broker authority. */
    NOT_AUTHORIZED,
    /** FMCSA has no entity with this MC number. */
    NOT_FOUND
}
