package com.envisione.progressiveskills.common.transaction;

import java.time.Instant;
import java.util.Objects;

/** Successful transition-delivery tombstone. */
public record GrantReceipt(
        ReceiptKey key,
        TransactionId transactionId,
        DefinitionRevision definitionRevision,
        Instant deliveredAt,
        DeliveryContract deliveryContract,
        String detail
) {
    public GrantReceipt {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(transactionId, "transactionId");
        Objects.requireNonNull(definitionRevision, "definitionRevision");
        Objects.requireNonNull(deliveredAt, "deliveredAt");
        Objects.requireNonNull(deliveryContract, "deliveryContract");
        detail = Objects.requireNonNull(detail, "detail");
    }
}
