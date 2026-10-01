package com.team1.ecommerce.inventory.controller;

import com.team1.ecommerce.inventory.dto.StockAdjustment;
import com.team1.ecommerce.inventory.dto.StockCheck;
import com.team1.ecommerce.inventory.dto.StockView;
import com.team1.ecommerce.inventory.service.InventoryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/inventory")
public class InventoryController {
    private final InventoryService inventory;

    public InventoryController(InventoryService inventory) {
        this.inventory = inventory;
    }

    @GetMapping("/check")
    public StockCheck check(@RequestParam @Positive Long productId, @RequestParam @Positive int quantity) {
        return new StockCheck(inventory.available(productId, quantity));
    }

    @GetMapping("/{productId}")
    public StockView find(@PathVariable Long productId) {
        return inventory.find(productId);
    }

    @PutMapping("/{productId}")
    public StockView adjust(@PathVariable @Positive Long productId, @Valid @RequestBody StockAdjustment adjustment) {
        return inventory.adjust(productId, adjustment.available());
    }
}
