package com.retailops.inventory;

import com.retailops.inventory.InventoryDtos.InventoryAdjustmentRequest;
import com.retailops.inventory.InventoryDtos.InventoryAlertResponse;
import com.retailops.inventory.InventoryDtos.PurchaseRequest;
import com.retailops.inventory.InventoryDtos.SaleReservationRequest;
import com.retailops.inventory.InventoryDtos.StockLevelResponse;
import com.retailops.inventory.InventoryDtos.StockMovementResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class InventoryService {
    private final JdbcTemplate jdbc;

    public InventoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<StockLevelResponse> listLevels(boolean lowOnly) {
        String sql = """
                SELECT product_sku, quantity, min_quantity, max_quantity, location, updated_at
                FROM stock_levels
                """;
        if (lowOnly) {
            sql += " WHERE quantity <= min_quantity";
        }
        sql += " ORDER BY quantity <= min_quantity DESC, updated_at DESC, product_sku";
        return jdbc.query(sql, stockLevelMapper());
    }

    public StockLevelResponse getLevel(String sku) {
        try {
            return jdbc.queryForObject("""
                    SELECT product_sku, quantity, min_quantity, max_quantity, location, updated_at
                    FROM stock_levels
                    WHERE product_sku = ?
                    """, stockLevelMapper(), sku);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock level not found");
        }
    }

    public List<StockMovementResponse> listMovements(String sku) {
        if (sku == null || sku.isBlank()) {
            return jdbc.query("""
                    SELECT id, product_sku, movement_type, quantity_delta, unit_cost, reference_type,
                           reference_id, note, created_at
                    FROM stock_movements
                    ORDER BY created_at DESC, id DESC
                    LIMIT 300
                    """, stockMovementMapper());
        }
        return jdbc.query("""
                SELECT id, product_sku, movement_type, quantity_delta, unit_cost, reference_type,
                       reference_id, note, created_at
                FROM stock_movements
                WHERE product_sku = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 300
                """, stockMovementMapper(), sku);
    }

    @Transactional
    public StockLevelResponse adjust(InventoryAdjustmentRequest request) {
        applyDelta(
                request.productSku(),
                request.quantityDelta(),
                "ADJUSTMENT",
                request.unitCost(),
                "MANUAL",
                blankToDefault(request.referenceId(), "manual-adjustment"),
                request.note()
        );
        return getLevel(request.productSku());
    }

    @Transactional
    public StockLevelResponse purchase(PurchaseRequest request) {
        applyDelta(
                request.productSku(),
                request.quantity(),
                "PURCHASE",
                request.unitCost(),
                "PURCHASE",
                blankToDefault(request.referenceId(), "purchase"),
                request.note()
        );
        return getLevel(request.productSku());
    }

    @Transactional
    public void reserveSale(SaleReservationRequest request) {
        for (var item : request.items()) {
            Map<String, Object> stock = lockStock(item.productSku());
            int quantity = ((Number) stock.get("quantity")).intValue();
            if (quantity < item.quantity()) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Insufficient stock for " + item.productSku()
                );
            }
        }
        for (var item : request.items()) {
            applyDelta(
                    item.productSku(),
                    -item.quantity(),
                    "SALE",
                    null,
                    "SALES_ORDER",
                    request.orderNo(),
                    "POS checkout"
            );
        }
    }

    public List<InventoryAlertResponse> listAlerts(boolean includeResolved) {
        String sql = """
                SELECT id, product_sku, alert_type, severity, message, resolved, created_at, resolved_at
                FROM inventory_alerts
                """;
        if (!includeResolved) {
            sql += " WHERE resolved = FALSE";
        }
        sql += " ORDER BY resolved ASC, created_at DESC LIMIT 200";
        return jdbc.query(sql, alertMapper());
    }

    public void resolveAlert(Long id) {
        int updated = jdbc.update("""
                UPDATE inventory_alerts
                SET resolved = TRUE, resolved_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """, id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Alert not found");
        }
    }

    private void applyDelta(
            String sku,
            int delta,
            String movementType,
            BigDecimal unitCost,
            String referenceType,
            String referenceId,
            String note
    ) {
        ensureStockRow(sku);
        Map<String, Object> stock = lockStock(sku);
        int before = ((Number) stock.get("quantity")).intValue();
        int minQuantity = ((Number) stock.get("min_quantity")).intValue();
        int after = before + delta;
        if (after < 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Stock cannot become negative for " + sku);
        }
        jdbc.update("""
                UPDATE stock_levels
                SET quantity = ?, updated_at = CURRENT_TIMESTAMP
                WHERE product_sku = ?
                """, after, sku);
        jdbc.update("""
                INSERT INTO stock_movements
                    (product_sku, movement_type, quantity_delta, unit_cost, reference_type, reference_id, note)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, sku, movementType, delta, unitCost, referenceType, referenceId, note);
        if (after <= minQuantity) {
            raiseLowStockAlert(sku, after, minQuantity);
        }
    }

    private void ensureStockRow(String sku) {
        int exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM stock_levels WHERE product_sku = ?",
                Integer.class,
                sku
        );
        if (exists == 0) {
            jdbc.update("""
                    INSERT INTO stock_levels (product_sku, quantity, min_quantity, max_quantity, location)
                    VALUES (?, 0, 10, 200, 'MAIN')
                    """, sku);
        }
    }

    private Map<String, Object> lockStock(String sku) {
        try {
            return jdbc.queryForMap("""
                    SELECT product_sku, quantity, min_quantity, max_quantity
                    FROM stock_levels
                    WHERE product_sku = ?
                    FOR UPDATE
                    """, sku);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Stock level not found for " + sku);
        }
    }

    private void raiseLowStockAlert(String sku, int quantity, int minQuantity) {
        int openAlerts = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM inventory_alerts
                WHERE product_sku = ? AND resolved = FALSE AND alert_type = 'LOW_STOCK'
                """, Integer.class, sku);
        if (openAlerts == 0) {
            jdbc.update("""
                    INSERT INTO inventory_alerts (product_sku, alert_type, severity, message)
                    VALUES (?, 'LOW_STOCK', ?, ?)
                    """,
                    sku,
                    quantity == 0 ? "HIGH" : "MEDIUM",
                    "Stock for " + sku + " is " + quantity + ", below warning threshold " + minQuantity
            );
        }
    }

    private RowMapper<StockLevelResponse> stockLevelMapper() {
        return (rs, rowNum) -> new StockLevelResponse(
                rs.getString("product_sku"),
                rs.getInt("quantity"),
                rs.getInt("min_quantity"),
                rs.getInt("max_quantity"),
                rs.getString("location"),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private RowMapper<StockMovementResponse> stockMovementMapper() {
        return (rs, rowNum) -> new StockMovementResponse(
                rs.getLong("id"),
                rs.getString("product_sku"),
                rs.getString("movement_type"),
                rs.getInt("quantity_delta"),
                rs.getBigDecimal("unit_cost"),
                rs.getString("reference_type"),
                rs.getString("reference_id"),
                rs.getString("note"),
                toInstant(rs.getTimestamp("created_at"))
        );
    }

    private RowMapper<InventoryAlertResponse> alertMapper() {
        return (rs, rowNum) -> new InventoryAlertResponse(
                rs.getLong("id"),
                rs.getString("product_sku"),
                rs.getString("alert_type"),
                rs.getString("severity"),
                rs.getString("message"),
                rs.getBoolean("resolved"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("resolved_at"))
        );
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String blankToDefault(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
