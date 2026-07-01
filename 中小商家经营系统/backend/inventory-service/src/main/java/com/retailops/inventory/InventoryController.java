package com.retailops.inventory;

import com.retailops.inventory.InventoryDtos.InventoryAdjustmentRequest;
import com.retailops.inventory.InventoryDtos.InventoryAlertResponse;
import com.retailops.inventory.InventoryDtos.PurchaseRequest;
import com.retailops.inventory.InventoryDtos.SaleReservationRequest;
import com.retailops.inventory.InventoryDtos.StockLevelResponse;
import com.retailops.inventory.InventoryDtos.StockMovementResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {
    private final InventoryService service;

    public InventoryController(InventoryService service) {
        this.service = service;
    }

    @GetMapping("/levels")
    public List<StockLevelResponse> listLevels(@RequestParam(name = "lowOnly", defaultValue = "false") boolean lowOnly) {
        return service.listLevels(lowOnly);
    }

    @GetMapping("/levels/{sku}")
    public StockLevelResponse getLevel(@PathVariable("sku") String sku) {
        return service.getLevel(sku);
    }

    @GetMapping("/movements")
    public List<StockMovementResponse> listMovements(@RequestParam(name = "sku", required = false) String sku) {
        return service.listMovements(sku);
    }

    @PostMapping("/adjustments")
    public StockLevelResponse adjust(@Valid @RequestBody InventoryAdjustmentRequest request) {
        return service.adjust(request);
    }

    @PostMapping("/purchases")
    public StockLevelResponse purchase(@Valid @RequestBody PurchaseRequest request) {
        return service.purchase(request);
    }

    @PostMapping("/sale-reservations")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reserveSale(@Valid @RequestBody SaleReservationRequest request) {
        service.reserveSale(request);
    }

    @GetMapping("/alerts")
    public List<InventoryAlertResponse> listAlerts(@RequestParam(name = "includeResolved", defaultValue = "false") boolean includeResolved) {
        return service.listAlerts(includeResolved);
    }

    @PostMapping("/alerts/{id}/resolve")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resolve(@PathVariable("id") Long id) {
        service.resolveAlert(id);
    }
}
