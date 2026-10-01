package com.team1.ecommerce.product.repository;

import static org.assertj.core.api.Assertions.assertThat;

import com.team1.ecommerce.product.ProductServiceApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.context.ActiveProfiles;
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
}
