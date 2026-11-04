package com.clerova.trucking;

/** Where a load is, from rate con to paid. */
public enum LoadStatus {
    BOOKED,
    IN_TRANSIT,
    DELIVERED,
    INVOICED,
    PAID,
    CANCELLED
}
