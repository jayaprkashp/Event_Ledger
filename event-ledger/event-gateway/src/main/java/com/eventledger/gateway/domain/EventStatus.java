package com.eventledger.gateway.domain;

public enum EventStatus {
    PENDING,
    APPLIED,
    FAILED_DOWNSTREAM,
    VALIDATION_FAILED
}
