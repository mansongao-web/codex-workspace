package com.retailops.catalog.product;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.Instant;

public final class ProductDtos {
    private ProductDtos() {
    }

    public record ProductRequest(
            @NotBlank String sku,
            String barcode,
            @NotBlank String name,
            Long categoryId,
            Long supplierId,
            @NotBlank String unit,
            @NotNull @DecimalMin("0.00") BigDecimal costPrice,
            @NotNull @DecimalMin("0.00") BigDecimal retailPrice,
            String status
    ) {
    }

    public record ProductResponse(
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

    public record CategoryRequest(@NotBlank String name) {
    }

    public record CategoryResponse(Long id, String name) {
    }

    public record SupplierRequest(@NotBlank String name, String contactName, String phone) {
    }

    public record SupplierResponse(Long id, String name, String contactName, String phone) {
    }
}
