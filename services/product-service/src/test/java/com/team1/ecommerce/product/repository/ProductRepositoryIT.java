package com.team1.ecommerce.product.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team1.ecommerce.product.ProductServiceApplication;
import com.team1.ecommerce.product.dto.ProductRequest;
import com.team1.ecommerce.product.service.ProductService;
import com.team1.ecommerce.product.exception.CategoryNotFoundException;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ProductServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers
class ProductRepositoryIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Autowired
    ProductRepository products;

    @Autowired
    ProductService service;

    @Test
    void migrationSeedsTwentyProducts() {
        assertThat(products.count()).isEqualTo(20);
    }

    @Test
    void browseProjectsCategoryNameIntoPages() {
        var firstPage = products.browse(PageRequest.of(0, 10, Sort.by("id")));
        assertThat(firstPage.getTotalElements()).isEqualTo(20);
        assertThat(firstPage.getContent()).hasSize(10);
        assertThat(firstPage.getContent().getFirst().categoryName()).isEqualTo("Electronics");
    }

    @Test
    void detailProjectsCategoryName() {
        assertThat(products.findViewById(1L)).get().extracting("categoryName").isEqualTo("Electronics");
    }

    @Test
    @Transactional
    void createCanBeReadBackWithCategoryName() {
        var created = service.create(new ProductRequest("Monitor Stand", new BigDecimal("25.50"), 1L));
        assertThat(service.findById(created.id()).categoryName()).isEqualTo("Electronics");
    }

    @Test
    @Transactional
    void updateChangesPrice() {
        var created = service.create(new ProductRequest("Monitor Stand", new BigDecimal("25.50"), 1L));
        service.update(created.id(), new ProductRequest("Monitor Stand", new BigDecimal("29.75"), 1L));
        assertThat(service.findById(created.id()).price()).isEqualByComparingTo("29.75");
    }

    @Test
    @Transactional
    void deleteRemovesProduct() {
        var created = service.create(new ProductRequest("Monitor Stand", new BigDecimal("25.50"), 1L));
        service.delete(created.id());
        assertThat(products.findById(created.id())).isEmpty();
    }

    @Test
    @Transactional
    void unknownCategoryIsRejected() {
        assertThatThrownBy(() -> service.create(new ProductRequest("Monitor Stand", new BigDecimal("25.50"), -1L)))
                .isInstanceOf(CategoryNotFoundException.class);
    }
}
