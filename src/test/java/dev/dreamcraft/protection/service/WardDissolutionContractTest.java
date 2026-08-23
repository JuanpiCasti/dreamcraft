package dev.dreamcraft.protection.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Truth table of {@link WardDissolutionService#shouldRefund(boolean, boolean)}:
 * the tagged founder item only returns when the OWNER dissolves their own ward
 * AND the physical core block was actually removed (anti-duplication guard).
 */
class WardDissolutionContractTest {

    @Test
    void refundsOwnerWhenCoreBlockRemoved() {
        assertEquals(true, WardDissolutionService.shouldRefund(true, true));
    }

    @Test
    void doesNotRefundOwnerWithoutPhysicalCore() {
        assertEquals(false, WardDissolutionService.shouldRefund(true, false));
    }

    @Test
    void neverRefundsAdminTeardownEvenWithCoreRemoved() {
        assertEquals(false, WardDissolutionService.shouldRefund(false, true));
    }

    @Test
    void neverRefundsAdminWithoutCore() {
        assertEquals(false, WardDissolutionService.shouldRefund(false, false));
    }
}
