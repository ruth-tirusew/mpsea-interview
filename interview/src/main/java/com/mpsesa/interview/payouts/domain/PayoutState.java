package com.mpsesa.interview.payouts.domain;

/** Internal lifecycle. RESERVED -> SUBMITTING -> SUBMITTED | UNKNOWN -> SUCCEEDED | FAILED. */
public enum PayoutState {
    RESERVED, SUBMITTING, SUBMITTED, UNKNOWN, SUCCEEDED, FAILED;

    public boolean isFinal() {
        return this == SUCCEEDED || this == FAILED;
    }

    /** Public status: PENDING, SUCCEEDED or FAILED. */
    public String publicStatus() {
        return isFinal() ? name() : "PENDING";
    }
}
