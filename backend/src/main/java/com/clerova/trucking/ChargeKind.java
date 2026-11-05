package com.clerova.trucking;

/** Money on a load besides the rate. */
public enum ChargeKind {
    DETENTION("Detention"),
    LUMPER("Lumper"),
    TONU("Truck ordered, not used"),
    LAYOVER("Layover"),
    OTHER("Other");

    private final String label;

    ChargeKind(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }
}
