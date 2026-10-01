package com.team1.ecommerce.order.service;

import com.team1.ecommerce.order.client.ProtectedClients;
import com.team1.ecommerce.order.dto.OrderPage;
import com.team1.ecommerce.order.dto.OrderRequest;
import com.team1.ecommerce.order.dto.OrderView;
import com.team1.ecommerce.order.entity.Order;
import com.team1.ecommerce.order.exception.OrderException;
import com.team1.ecommerce.order.repository.OrderRepository;
import feign.FeignException;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionException;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {
    private final ProtectedClients clients;
    private final OrderWriter writer;
    private final OrderRepository orders;

    public OrderService(ProtectedClients clients, OrderWriter writer, OrderRepository orders) {
        this.clients = clients;
        this.writer = writer;
        this.orders = orders;
    }

    public UUID place(String customerId, OrderRequest request) {
        var seen = new HashSet<Long>();
        var priced = new ArrayList<OrderWriter.PricedItem>();
        for (var item : request.items()) {
            if (item == null || item.productId() == null || item.quantity() == null
                    || item.productId() < 1 || item.quantity() < 1) {
                throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid order item");
            }
            if (!seen.add(item.productId())) throw new OrderException(HttpStatus.BAD_REQUEST, "Duplicate productId");
            BigDecimal price;
            try {
                price = clients.price(item.productId()).join().price();
            } catch (RuntimeException ex) {
                if (root(ex) instanceof FeignException.NotFound) {
                    throw new OrderException(HttpStatus.BAD_REQUEST, "Unknown productId " + item.productId());
                }
                throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE, "Product unavailable");
            }
            if (price == null || price.signum() <= 0 || price.scale() > 2) {
                throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE, "Invalid product price");
            }
            try {
                if (!clients.available(item.productId(), item.quantity()).join().available()) {
                    throw new OrderException(HttpStatus.CONFLICT, "Out of stock");
                }
            } catch (OrderException ex) {
                throw ex;
            } catch (RuntimeException ex) {
                throw new OrderException(HttpStatus.SERVICE_UNAVAILABLE, "Inventory unavailable");
            }
            priced.add(new OrderWriter.PricedItem(item.productId(), item.quantity(), price));
        }
        // ponytail: a synchronous availability check can race reservation; L3 inventory reservation resolves contention.
        return writer.save(customerId, priced);
    }

    @Transactional(readOnly = true)
    public OrderView get(UUID id, String customerId) {
        return view(orders.findByIdAndCustomerId(id, customerId)
                .orElseThrow(() -> new OrderException(HttpStatus.NOT_FOUND, "Order not found")));
    }

    @Transactional(readOnly = true)
    public OrderPage list(String customerId, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new OrderException(HttpStatus.BAD_REQUEST, "Invalid paging");
        return OrderPage.from(orders.findByCustomerId(customerId,
                PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt", "id"))).map(this::view));
    }

    private OrderView view(Order order) {
        return new OrderView(order.getId(), order.getStatus(), order.getTotalAmount(), order.getCreatedAt(),
                order.getItems().stream().map(item -> new OrderView.Item(item.getProductId(), item.getQuantity(),
                        item.getUnitPrice())).toList());
    }

    private Throwable root(Throwable ex) {
        while (ex instanceof CompletionException && ex.getCause() != null) ex = ex.getCause();
        return ex;
    }
}
