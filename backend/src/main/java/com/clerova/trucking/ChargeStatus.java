package com.clerova.trucking;

/** Where a charge is with the broker. Only approved charges go on the invoice. */
public enum ChargeStatus {
    TO_CLAIM,
    CLAIMED,
    APPROVED,
    DENIED
}
