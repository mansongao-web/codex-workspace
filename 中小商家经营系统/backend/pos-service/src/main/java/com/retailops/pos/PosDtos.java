package com.retailops.pos;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public final class PosDtos {
    private PosDtos() {
    }

    public record CheckoutRequest(
            @NotBlank String cashier,
            @NotBlank String paymentMethod,
            BigDecimal discountAmount,
            @NotEmpty List<@Valid CheckoutItemRequest> items
    ) {
    }

    public record CheckoutItemRequest(
            @NotBlank String skuOrBarcode,
            @NotNull @Positive Integer quantity,
            BigDecimal manualUnitPrice
    ) {
    }

    public record CheckoutItemResponse(
            String productSku,
            String productName,
            Integer quantity,
            BigDecimal unitPrice,
            BigDecimal unitCost,
            BigDecimal lineAmount,
            BigDecimal profitAmount
    ) {
    }

    public record CheckoutResponse(
            String orderNo,
            LocalDate businessDate,
            BigDecimal subtotalAmount,
            BigDecimal discountAmount,
            BigDecimal payableAmount,
            BigDecimal costAmount,
            BigDecimal profitAmount,
            String paymentMethod,
            String cashier,
            String status,
            Instant createdAt,
            List<CheckoutItemResponse> items
    ) {
    }

    public record ProductSnapshot(
            Long id,
            String sku,
            String barcode,
            String name,
            Long categoryId,
            String categoryName,
            Long supplierId,
            String supplierName,
            String unit,
            BigDecimal costPrice,
            BigDecimal retailPrice,
            String status,
            Instant createdAt,
            Instant updatedAt
    ) {
    }

    public record SaleReservation(String orderNo, List<SaleReservationItem> items) {
    }

    public record SaleReservationItem(String productSku, Integer quantity) {
    }
}
