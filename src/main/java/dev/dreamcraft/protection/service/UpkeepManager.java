package dev.dreamcraft.protection.service;

import dev.dreamcraft.protection.config.ProtectionConfig;
import dev.dreamcraft.protection.model.ProtectionClaim;
import dev.dreamcraft.protection.model.ProtectionState;

import java.time.Instant;

public final class UpkeepManager {
    private final ProtectionConfig config;
    private final UpkeepCalculator calculator;

    public UpkeepManager(ProtectionConfig config, UpkeepCalculator calculator) {
        this.config = config;
        this.calculator = calculator;
    }

    public void recalculateState(ProtectionClaim claim) {
        UpkeepSnapshot snapshot = calculator.calculate(claim);
        Instant now = Instant.now();
        if (snapshot.storedUnits() <= 0) {
            Instant unprotectedAt = claim.nextUpkeepAt().plus(config.gracePeriod());
            Instant decayingAt = unprotectedAt.plus(config.destructionDelay());
            if (now.isAfter(decayingAt)) {
                claim.status(ProtectionState.DECAYING);
            } else if (now.isAfter(unprotectedAt)) {
                claim.status(ProtectionState.UNPROTECTED);
            } else {
                claim.status(ProtectionState.NO_RESOURCES);
            }
            return;
        }
        if (snapshot.timeRemaining().compareTo(config.expiringThreshold()) <= 0) {
            claim.status(ProtectionState.EXPIRING);
        } else if (snapshot.timeRemaining().compareTo(config.warningThreshold()) <= 0) {
            claim.status(ProtectionState.WARNING);
        } else {
            claim.status(ProtectionState.ACTIVE);
        }
        claim.lastUpkeepAt(now);
        claim.nextUpkeepAt(now.plus(snapshot.timeRemaining()));
    }
}
