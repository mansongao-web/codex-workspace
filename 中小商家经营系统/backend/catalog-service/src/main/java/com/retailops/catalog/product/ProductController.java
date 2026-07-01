package com.retailops.catalog.product;

import com.retailops.catalog.product.ProductDtos.CategoryRequest;
import com.retailops.catalog.product.ProductDtos.CategoryResponse;
import com.retailops.catalog.product.ProductDtos.ProductRequest;
import com.retailops.catalog.product.ProductDtos.ProductResponse;
import com.retailops.catalog.product.ProductDtos.SupplierRequest;
import com.retailops.catalog.product.ProductDtos.SupplierResponse;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api")
public class ProductController {
    private final ProductService service;

    public ProductController(ProductService service) {
        this.service = service;
    }

    @GetMapping("/products")
    public List<ProductResponse> listProducts(
            @RequestParam(name = "keyword", required = false) String keyword,
            @RequestParam(name = "status", required = false) String status
    ) {
        return service.listProducts(keyword, status);
    }

    @GetMapping("/products/{id}")
    public ProductResponse getProduct(@PathVariable("id") Long id) {
        return service.getProduct(id);
    }

    @GetMapping("/products/by-sku/{sku}")
    public ProductResponse bySku(@PathVariable("sku") String sku) {
        return service.lookup(sku);
    }

    @GetMapping("/products/lookup/{skuOrBarcode}")
    public ProductResponse lookup(@PathVariable("skuOrBarcode") String skuOrBarcode) {
        return service.lookup(skuOrBarcode);
    }

    @PostMapping("/products")
    public ProductResponse createProduct(@Valid @RequestBody ProductRequest request) {
        return service.createProduct(request);
    }

    @PutMapping("/products/{id}")
    public ProductResponse updateProduct(@PathVariable("id") Long id, @Valid @RequestBody ProductRequest request) {
        return service.updateProduct(id, request);
    }

    @GetMapping("/categories")
    public List<CategoryResponse> listCategories() {
        return service.listCategories();
    }

    @PostMapping("/categories")
    public CategoryResponse createCategory(@Valid @RequestBody CategoryRequest request) {
        return service.createCategory(request);
    }

    @GetMapping("/suppliers")
    public List<SupplierResponse> listSuppliers() {
        return service.listSuppliers();
    }

    @PostMapping("/suppliers")
    public SupplierResponse createSupplier(@Valid @RequestBody SupplierRequest request) {
        return service.createSupplier(request);
    }
}
