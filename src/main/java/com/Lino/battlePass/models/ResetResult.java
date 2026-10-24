package com.Lino.battlePass.models;

/** A reset request completes only after its committed state has been applied. Failures complete exceptionally. */
public enum ResetResult {
    COMPLETED, BUSY, COOLDOWN, STOPPED, NOT_READY
}
