package com.retailops.inventory;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public final class InventoryDtos {
    private InventoryDtos() {
    }

    public record StockLevelResponse(
            String productSku,
            Integer quantity,
            Integer minQuantity,
            Integer maxQuantity,
            String location,
            Instant updatedAt
    ) {
    }

    public record StockMovementResponse(
            Long id,
            String productSku,
            String movementType,
            Integer quantityDelta,
            BigDecimal unitCost,
            String referenceType,
            String referenceId,
            String note,
            Instant createdAt
    ) {
    }

    public record InventoryAdjustmentRequest(
            @NotBlank String productSku,
            @NotNull Integer quantityDelta,
            BigDecimal unitCost,
            String referenceId,
            String note
    ) {
    }

    public record PurchaseRequest(
            @NotBlank String productSku,
            @NotNull @Positive Integer quantity,
            BigDecimal unitCost,
            String referenceId,
            String note
    ) {
    }

    public record SaleReservationRequest(
            @NotBlank String orderNo,
            @NotEmpty List<@Valid SaleReservationItem> items
    ) {
    }

    public record SaleReservationItem(@NotBlank String productSku, @NotNull @Positive Integer quantity) {
    }

    public record InventoryAlertResponse(
            Long id,
            String productSku,
            String alertType,
            String severity,
            String message,
            Boolean resolved,
            Instant createdAt,
            Instant resolvedAt
    ) {
    }
}
