package com.retailops.pos;

import com.retailops.pos.PosDtos.CheckoutItemRequest;
import com.retailops.pos.PosDtos.CheckoutItemResponse;
import com.retailops.pos.PosDtos.CheckoutRequest;
import com.retailops.pos.PosDtos.CheckoutResponse;
import com.retailops.pos.PosDtos.ProductSnapshot;
import com.retailops.pos.PosDtos.SaleReservation;
import com.retailops.pos.PosDtos.SaleReservationItem;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;

@Service
public class PosService {
    private final JdbcTemplate jdbc;
    private final RestClient restClient;
    private final String catalogUrl;
    private final String inventoryUrl;

    public PosService(
            JdbcTemplate jdbc,
            RestClient restClient,
            @Value("${services.catalog-url}") String catalogUrl,
            @Value("${services.inventory-url}") String inventoryUrl
    ) {
        this.jdbc = jdbc;
        this.restClient = restClient;
        this.catalogUrl = catalogUrl;
        this.inventoryUrl = inventoryUrl;
    }

    public ProductSnapshot lookup(String skuOrBarcode) {
        try {
            return restClient.get()
                    .uri(catalogUrl + "/api/products/lookup/{value}", skuOrBarcode)
                    .retrieve()
                    .body(ProductSnapshot.class);
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    @Transactional
    public CheckoutResponse checkout(CheckoutRequest request) {
        List<CheckoutItemResponse> itemResponses = new ArrayList<>();
        List<SaleReservationItem> reservationItems = new ArrayList<>();
        BigDecimal subtotal = BigDecimal.ZERO;
        BigDecimal costTotal = BigDecimal.ZERO;

        for (CheckoutItemRequest item : request.items()) {
            ProductSnapshot product = lookup(item.skuOrBarcode());
            if (!"ACTIVE".equals(product.status())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT, product.name() + " is not active");
            }
            BigDecimal quantity = BigDecimal.valueOf(item.quantity());
            BigDecimal unitPrice = item.manualUnitPrice() == null ? product.retailPrice() : item.manualUnitPrice();
            BigDecimal lineAmount = money(unitPrice.multiply(quantity));
            BigDecimal lineCost = money(product.costPrice().multiply(quantity));
            BigDecimal lineProfit = money(lineAmount.subtract(lineCost));
            subtotal = subtotal.add(lineAmount);
            costTotal = costTotal.add(lineCost);
            itemResponses.add(new CheckoutItemResponse(
                    product.sku(),
                    product.name(),
                    item.quantity(),
                    unitPrice,
                    product.costPrice(),
                    lineAmount,
                    lineProfit
            ));
            reservationItems.add(new SaleReservationItem(product.sku(), item.quantity()));
        }

        BigDecimal discount = request.discountAmount() == null ? BigDecimal.ZERO : request.discountAmount();
        if (discount.compareTo(BigDecimal.ZERO) < 0 || discount.compareTo(subtotal) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Discount must be between 0 and subtotal");
        }
        BigDecimal payable = money(subtotal.subtract(discount));
        BigDecimal profit = money(payable.subtract(costTotal));
        String orderNo = generateOrderNo();

        reserveInventory(new SaleReservation(orderNo, reservationItems));

        KeyHolder keyHolder = new GeneratedKeyHolder();
        LocalDate businessDate = LocalDate.now();
        Instant now = Instant.now();
        BigDecimal finalSubtotal = money(subtotal);
        BigDecimal finalCostTotal = money(costTotal);
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO sales_orders
                        (order_no, business_date, subtotal_amount, discount_amount, payable_amount,
                         cost_amount, profit_amount, payment_method, cashier, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'PAID')
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, orderNo);
            ps.setObject(2, businessDate);
            ps.setBigDecimal(3, finalSubtotal);
            ps.setBigDecimal(4, discount);
            ps.setBigDecimal(5, payable);
            ps.setBigDecimal(6, finalCostTotal);
            ps.setBigDecimal(7, profit);
            ps.setString(8, request.paymentMethod());
            ps.setString(9, request.cashier());
            return ps;
        }, keyHolder);

        long orderId = Objects.requireNonNull(keyHolder.getKey()).longValue();
        for (CheckoutItemResponse item : itemResponses) {
            jdbc.update("""
                    INSERT INTO sales_order_items
                        (order_id, product_sku, product_name, quantity, unit_price, unit_cost,
                         line_amount, profit_amount)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                    """,
                    orderId,
                    item.productSku(),
                    item.productName(),
                    item.quantity(),
                    item.unitPrice(),
                    item.unitCost(),
                    item.lineAmount(),
                    item.profitAmount());
        }

        return new CheckoutResponse(
                orderNo,
                businessDate,
                finalSubtotal,
                discount,
                payable,
                finalCostTotal,
                profit,
                request.paymentMethod(),
                request.cashier(),
                "PAID",
                now,
                itemResponses
        );
    }

    public List<CheckoutResponse> listOrders(LocalDate date) {
        LocalDate businessDate = date == null ? LocalDate.now() : date;
        return jdbc.query("""
                SELECT id, order_no, business_date, subtotal_amount, discount_amount, payable_amount,
                       cost_amount, profit_amount, payment_method, cashier, status, created_at
                FROM sales_orders
                WHERE business_date = ?
                ORDER BY created_at DESC, id DESC
                LIMIT 200
                """, orderMapper(false), businessDate);
    }

    public CheckoutResponse getOrder(String orderNo) {
        try {
            CheckoutResponse order = jdbc.queryForObject("""
                    SELECT id, order_no, business_date, subtotal_amount, discount_amount, payable_amount,
                           cost_amount, profit_amount, payment_method, cashier, status, created_at
                    FROM sales_orders
                    WHERE order_no = ?
                    """, orderMapper(false), orderNo);
            Long orderId = jdbc.queryForObject("SELECT id FROM sales_orders WHERE order_no = ?", Long.class, orderNo);
            List<CheckoutItemResponse> items = jdbc.query("""
                    SELECT product_sku, product_name, quantity, unit_price, unit_cost, line_amount, profit_amount
                    FROM sales_order_items
                    WHERE order_id = ?
                    ORDER BY id
                    """, itemMapper(), orderId);
            return new CheckoutResponse(
                    order.orderNo(),
                    order.businessDate(),
                    order.subtotalAmount(),
                    order.discountAmount(),
                    order.payableAmount(),
                    order.costAmount(),
                    order.profitAmount(),
                    order.paymentMethod(),
                    order.cashier(),
                    order.status(),
                    order.createdAt(),
                    items
            );
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Order not found");
        }
    }

    private void reserveInventory(SaleReservation reservation) {
        try {
            restClient.post()
                    .uri(inventoryUrl + "/api/inventory/sale-reservations")
                    .body(reservation)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Inventory reservation failed");
        }
    }

    private RowMapper<CheckoutResponse> orderMapper(boolean withItems) {
        return (rs, rowNum) -> new CheckoutResponse(
                rs.getString("order_no"),
                rs.getObject("business_date", LocalDate.class),
                rs.getBigDecimal("subtotal_amount"),
                rs.getBigDecimal("discount_amount"),
                rs.getBigDecimal("payable_amount"),
                rs.getBigDecimal("cost_amount"),
                rs.getBigDecimal("profit_amount"),
                rs.getString("payment_method"),
                rs.getString("cashier"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                withItems ? List.of() : List.of()
        );
    }

    private RowMapper<CheckoutItemResponse> itemMapper() {
        return (rs, rowNum) -> new CheckoutItemResponse(
                rs.getString("product_sku"),
                rs.getString("product_name"),
                rs.getInt("quantity"),
                rs.getBigDecimal("unit_price"),
                rs.getBigDecimal("unit_cost"),
                rs.getBigDecimal("line_amount"),
                rs.getBigDecimal("profit_amount")
        );
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(2, RoundingMode.HALF_UP);
    }

    private String generateOrderNo() {
        String timestamp = DateTimeFormatter.ofPattern("yyyyMMddHHmmss").format(LocalDateTime.now());
        int suffix = ThreadLocalRandom.current().nextInt(1000, 9999);
        return "SO" + timestamp + suffix;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
