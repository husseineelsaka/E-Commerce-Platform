package com.team1.ecommerce.product.dto;

import java.util.List;
import org.springframework.data.domain.Page;

public record ProductPage(List<ProductView> content, PageMetadata page) {
    public static ProductPage from(Page<ProductView> products) {
        return new ProductPage(products.getContent(), new PageMetadata(products.getSize(),
                products.getNumber(), products.getTotalElements(), products.getTotalPages()));
    }

    public record PageMetadata(int size, int number, long totalElements, int totalPages) {
    }
}
