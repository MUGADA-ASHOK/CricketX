package com.ipl.event.enums;

/**
 * CONFIGURATION_PENDING -> WAITING_FOR_APPROVAL -> APPROVED -> TICKETS_OPEN -> COMPLETED
 *                                                                            -> CANCELLED (from any pre-COMPLETED state)
 */
public enum MatchStatus {
    CONFIGURATION_PENDING,
    WAITING_FOR_APPROVAL,
    APPROVED,
    TICKETS_OPEN,
    COMPLETED,
    CANCELLED
}
