package com.retailops.pos;

import com.retailops.pos.PosDtos.CheckoutRequest;
import com.retailops.pos.PosDtos.CheckoutResponse;
import com.retailops.pos.PosDtos.ProductSnapshot;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/pos")
public class PosController {
    private final PosService service;

    public PosController(PosService service) {
        this.service = service;
    }

    @GetMapping("/lookup/{skuOrBarcode}")
    public ProductSnapshot lookup(@PathVariable("skuOrBarcode") String skuOrBarcode) {
        return service.lookup(skuOrBarcode);
    }

    @PostMapping("/checkout")
    public CheckoutResponse checkout(@Valid @RequestBody CheckoutRequest request) {
        return service.checkout(request);
    }

    @GetMapping("/orders")
    public List<CheckoutResponse> listOrders(
            @RequestParam(name = "date", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date
    ) {
        return service.listOrders(date);
    }

    @GetMapping("/orders/{orderNo}")
    public CheckoutResponse getOrder(@PathVariable("orderNo") String orderNo) {
        return service.getOrder(orderNo);
    }
}
