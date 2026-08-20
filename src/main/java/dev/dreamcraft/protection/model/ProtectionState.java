package dev.dreamcraft.protection.model;

public enum ProtectionState {
    ACTIVE,
    WARNING,
    EXPIRING,
    NO_RESOURCES,
    UNPROTECTED,
    DECAYING,
    DESTROYED;

    public boolean isProtectionActive() {
        return this == ACTIVE || this == WARNING || this == EXPIRING;
    }
}
