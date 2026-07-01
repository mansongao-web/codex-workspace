package com.retailops.catalog.product;

import com.retailops.catalog.product.ProductDtos.CategoryRequest;
import com.retailops.catalog.product.ProductDtos.CategoryResponse;
import com.retailops.catalog.product.ProductDtos.ProductRequest;
import com.retailops.catalog.product.ProductDtos.ProductResponse;
import com.retailops.catalog.product.ProductDtos.SupplierRequest;
import com.retailops.catalog.product.ProductDtos.SupplierResponse;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Service
public class ProductService {
    private final JdbcTemplate jdbc;

    public ProductService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<ProductResponse> listProducts(String keyword, String status) {
        StringBuilder sql = new StringBuilder("""
                SELECT p.*, c.name AS category_name, s.name AS supplier_name
                FROM products p
                LEFT JOIN categories c ON c.id = p.category_id
                LEFT JOIN suppliers s ON s.id = p.supplier_id
                WHERE 1 = 1
                """);
        List<Object> params = new ArrayList<>();

        if (keyword != null && !keyword.isBlank()) {
            sql.append(" AND (p.name LIKE ? OR p.sku LIKE ? OR p.barcode LIKE ?)");
            String like = "%" + keyword.trim() + "%";
            params.add(like);
            params.add(like);
            params.add(like);
        }
        if (status != null && !status.isBlank()) {
            sql.append(" AND p.status = ?");
            params.add(status.trim().toUpperCase());
        }
        sql.append(" ORDER BY p.updated_at DESC, p.id DESC LIMIT 300");
        return jdbc.query(sql.toString(), productMapper(), params.toArray());
    }

    public ProductResponse getProduct(Long id) {
        try {
            return jdbc.queryForObject("""
                    SELECT p.*, c.name AS category_name, s.name AS supplier_name
                    FROM products p
                    LEFT JOIN categories c ON c.id = p.category_id
                    LEFT JOIN suppliers s ON s.id = p.supplier_id
                    WHERE p.id = ?
                    """, productMapper(), id);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    public ProductResponse lookup(String skuOrBarcode) {
        try {
            return jdbc.queryForObject("""
                    SELECT p.*, c.name AS category_name, s.name AS supplier_name
                    FROM products p
                    LEFT JOIN categories c ON c.id = p.category_id
                    LEFT JOIN suppliers s ON s.id = p.supplier_id
                    WHERE p.sku = ? OR p.barcode = ?
                    LIMIT 1
                    """, productMapper(), skuOrBarcode, skuOrBarcode);
        } catch (EmptyResultDataAccessException ex) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
    }

    public ProductResponse createProduct(ProductRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO products
                        (sku, barcode, name, category_id, supplier_id, unit, cost_price, retail_price, status)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            bindProduct(ps, request);
            return ps;
        }, keyHolder);
        return getProduct(Objects.requireNonNull(keyHolder.getKey()).longValue());
    }

    public ProductResponse updateProduct(Long id, ProductRequest request) {
        int updated = jdbc.update("""
                UPDATE products
                SET sku = ?, barcode = ?, name = ?, category_id = ?, supplier_id = ?, unit = ?,
                    cost_price = ?, retail_price = ?, status = ?, updated_at = CURRENT_TIMESTAMP
                WHERE id = ?
                """,
                request.sku(), emptyToNull(request.barcode()), request.name(), request.categoryId(),
                request.supplierId(), request.unit(), request.costPrice(), request.retailPrice(),
                normalizeStatus(request.status()), id);
        if (updated == 0) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Product not found");
        }
        return getProduct(id);
    }

    public List<CategoryResponse> listCategories() {
        return jdbc.query("SELECT id, name FROM categories ORDER BY name", categoryMapper());
    }

    public CategoryResponse createCategory(CategoryRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO categories (name) VALUES (?)",
                    Statement.RETURN_GENERATED_KEYS
            );
            ps.setString(1, request.name());
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return jdbc.queryForObject("SELECT id, name FROM categories WHERE id = ?", categoryMapper(), id);
    }

    public List<SupplierResponse> listSuppliers() {
        return jdbc.query("SELECT id, name, contact_name, phone FROM suppliers ORDER BY name", supplierMapper());
    }

    public SupplierResponse createSupplier(SupplierRequest request) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement("""
                    INSERT INTO suppliers (name, contact_name, phone) VALUES (?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, request.name());
            ps.setString(2, emptyToNull(request.contactName()));
            ps.setString(3, emptyToNull(request.phone()));
            return ps;
        }, keyHolder);
        Long id = Objects.requireNonNull(keyHolder.getKey()).longValue();
        return jdbc.queryForObject("""
                SELECT id, name, contact_name, phone FROM suppliers WHERE id = ?
                """, supplierMapper(), id);
    }

    private void bindProduct(PreparedStatement ps, ProductRequest request) throws SQLException {
        ps.setString(1, request.sku());
        ps.setString(2, emptyToNull(request.barcode()));
        ps.setString(3, request.name());
        if (request.categoryId() == null) {
            ps.setObject(4, null);
        } else {
            ps.setLong(4, request.categoryId());
        }
        if (request.supplierId() == null) {
            ps.setObject(5, null);
        } else {
            ps.setLong(5, request.supplierId());
        }
        ps.setString(6, request.unit());
        ps.setBigDecimal(7, request.costPrice());
        ps.setBigDecimal(8, request.retailPrice());
        ps.setString(9, normalizeStatus(request.status()));
    }

    private RowMapper<ProductResponse> productMapper() {
        return (rs, rowNum) -> new ProductResponse(
                rs.getLong("id"),
                rs.getString("sku"),
                rs.getString("barcode"),
                rs.getString("name"),
                nullableLong(rs, "category_id"),
                rs.getString("category_name"),
                nullableLong(rs, "supplier_id"),
                rs.getString("supplier_name"),
                rs.getString("unit"),
                rs.getBigDecimal("cost_price"),
                rs.getBigDecimal("retail_price"),
                rs.getString("status"),
                toInstant(rs.getTimestamp("created_at")),
                toInstant(rs.getTimestamp("updated_at"))
        );
    }

    private RowMapper<CategoryResponse> categoryMapper() {
        return (rs, rowNum) -> new CategoryResponse(rs.getLong("id"), rs.getString("name"));
    }

    private RowMapper<SupplierResponse> supplierMapper() {
        return (rs, rowNum) -> new SupplierResponse(
                rs.getLong("id"),
                rs.getString("name"),
                rs.getString("contact_name"),
                rs.getString("phone")
        );
    }

    private Long nullableLong(ResultSet rs, String column) throws SQLException {
        long value = rs.getLong(column);
        return rs.wasNull() ? null : value;
    }

    private Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }

    private String normalizeStatus(String status) {
        return status == null || status.isBlank() ? "ACTIVE" : status.trim().toUpperCase();
    }

    private String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
