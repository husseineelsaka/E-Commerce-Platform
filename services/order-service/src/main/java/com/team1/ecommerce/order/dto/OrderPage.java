package com.team1.ecommerce.order.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record OrderPage(List<OrderView> content, PageMetadata page) {
    public static OrderPage from(Page<OrderView> orders) {
        return new OrderPage(orders.getContent(), new PageMetadata(orders.getSize(), orders.getNumber(),
                orders.getTotalElements(), orders.getTotalPages()));
    }
    public record PageMetadata(int size, int number, long totalElements, int totalPages) {}
}
