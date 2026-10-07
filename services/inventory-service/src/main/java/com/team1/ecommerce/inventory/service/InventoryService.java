package com.team1.ecommerce.inventory.service;

import com.team1.ecommerce.inventory.dto.StockView;
import com.team1.ecommerce.inventory.entity.Stock;
import com.team1.ecommerce.inventory.exception.StockNotFoundException;
import com.team1.ecommerce.inventory.repository.InventoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class InventoryService {
    private final InventoryRepository stocks;

    public InventoryService(InventoryRepository stocks) {
        this.stocks = stocks;
    }

    public boolean available(Long productId, int quantity) {
        return stocks.findById(productId).map(stock -> stock.getAvailable() >= quantity).orElse(false);
    }

    public StockView find(Long productId) {
        return view(stocks.findById(productId).orElseThrow(() -> new StockNotFoundException(productId)));
    }

    // ponytail: unpaged; the demo catalogue has 20 rows. Add Pageable like the product list if it grows past one response.
    public List<StockView> lowStock(int threshold) {
        return stocks.findByAvailableLessThanOrderByAvailableAscProductIdAsc(threshold).stream().map(this::view).toList();
    }

    @Transactional
    public StockView adjust(Long productId, int available) {
        Stock stock = stocks.findById(productId).orElseGet(() -> new Stock(productId, available));
        stock.setAvailable(available);
        return view(stocks.saveAndFlush(stock));
    }

    private StockView view(Stock stock) {
        return new StockView(stock.getProductId(), stock.getAvailable(), stock.getReserved());
    }
}
