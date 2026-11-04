package com.clerova.trucking;

/** The FMCSA result for an MC number: its status and the names FMCSA lists. */
public record BrokerCheck(FmcsaStatus status, String legalName, String dbaName) {
}
