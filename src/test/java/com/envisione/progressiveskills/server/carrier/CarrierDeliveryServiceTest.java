package com.envisione.progressiveskills.server.carrier;

import com.envisione.progressiveskills.common.carrier.CarrierDeliveryPolicy;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CarrierDeliveryServiceTest {
    @Test
    void overflowPoliciesRouteTruthfully() {
        assertEquals(
                CarrierDeliveryService.OverflowRoute.REFUSE,
                CarrierDeliveryService.overflowRoute(
                        CarrierDeliveryPolicy.REFUSE_TRANSACTION, true
                )
        );
        assertEquals(
                CarrierDeliveryService.OverflowRoute.PENDING_CLAIM,
                CarrierDeliveryService.overflowRoute(
                        CarrierDeliveryPolicy.PENDING_CLAIM, false
                )
        );
        assertEquals(
                CarrierDeliveryService.OverflowRoute.DROP,
                CarrierDeliveryService.overflowRoute(
                        CarrierDeliveryPolicy.DROP_IF_SAFE, true
                )
        );
        assertEquals(
                CarrierDeliveryService.OverflowRoute.REFUSE,
                CarrierDeliveryService.overflowRoute(
                        CarrierDeliveryPolicy.DROP_IF_SAFE, false
                )
        );
        assertEquals(
                CarrierDeliveryService.OverflowRoute.UNSUPPORTED,
                CarrierDeliveryService.overflowRoute(
                        CarrierDeliveryPolicy.PROVIDER_MAIL, true
                )
        );
    }

    @Test
    void acquisitionResultsRequireOneMaterializedClaimPerRequestedItem() {
        UUID first = UUID.fromString("00000000-0000-0000-0000-000000001301");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000001302");
        var result = new CarrierDeliveryService.AcquisitionResult(
                true,
                CarrierDeliveryService.AcquisitionStatus.PENDING_CLAIM,
                2,
                0,
                List.of(first, second),
                "Stored two claims"
        );

        assertTrue(result.accepted());
        assertEquals(List.of(first, second), result.claimIds());
        assertThrows(IllegalArgumentException.class, () ->
                new CarrierDeliveryService.AcquisitionResult(
                        true,
                        CarrierDeliveryService.AcquisitionStatus.PENDING_CLAIM,
                        2,
                        0,
                        List.of(first),
                        "Incomplete claim batch"
                ));
    }

    @Test
    void resultRecordsRejectInvalidCountsAndPreserveStatus() {
        var claimResult = new CarrierDeliveryService.ClaimDeliveryResult(
                false,
                com.envisione.progressiveskills.common.carrier.PendingClaimTakeStatus.NOT_FOUND,
                "Unknown claim"
        );
        assertFalse(claimResult.accepted());
        assertEquals(
                com.envisione.progressiveskills.common.carrier.PendingClaimTakeStatus.NOT_FOUND,
                claimResult.status()
        );
        assertThrows(IllegalArgumentException.class, () ->
                new CarrierDeliveryService.AcquisitionResult(
                        true,
                        CarrierDeliveryService.AcquisitionStatus.DELIVERED,
                        1,
                        2,
                        List.of(),
                        "Invalid count"
                ));
        assertThrows(IllegalArgumentException.class, () ->
                new CarrierDeliveryService.ClaimBatchDeliveryResult(
                        false, -1, 0, "Invalid count"
                ));
    }
}
