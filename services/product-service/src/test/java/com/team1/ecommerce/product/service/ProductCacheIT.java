package com.team1.ecommerce.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.team1.ecommerce.product.ProductServiceApplication;
import com.team1.ecommerce.product.dto.ProductRequest;
import com.team1.ecommerce.product.exception.ProductNotFoundException;
import com.team1.ecommerce.product.repository.ProductRepository;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest(classes = ProductServiceApplication.class)
@ActiveProfiles("test")
@Testcontainers
class ProductCacheIT {
    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @Container
    static GenericContainer<?> redis = new GenericContainer<>("redis:7.4.5-alpine").withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", redis::getHost);
        registry.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
    }

    @Autowired
    ProductService service;

    @Autowired
    ProductRepository products;

    @Autowired
    StringRedisTemplate redisKeys;

    @BeforeEach
    void clearCache() {
        redisKeys.delete(redisKeys.keys("products*"));
    }

    @Test
    void updateEvictsItemAndList() {
        service.findById(1L);
        service.browse(0, 20);
        assertThat(redisKeys.keys("products*"))
                .contains("products:item::1", "products:list::0:20");

        service.update(1L, new ProductRequest("Wireless Headphones", new BigDecimal("89.99"), 1L));

        assertThat(redisKeys.keys("products*"))
                .doesNotContain("products:item::1", "products:list::0:20");
        assertThat(service.findById(1L).price()).isEqualByComparingTo("89.99");
        assertThat(service.browse(0, 20).content().getFirst().price()).isEqualByComparingTo("89.99");
    }

    @Test
    void secondDetailReadUsesCachedValue() {
        var first = service.findById(2L);
        assertThat(redisKeys.keys("products*")).contains("products:item::2");
        products.deleteById(2L);

        assertThat(service.findById(2L)).isEqualTo(first);
    }

    @Test
    void deleteEvictsItemAndList() {
        var created = service.create(new ProductRequest("Cache delete test", new BigDecimal("11.00"), 1L));
        service.findById(created.id());
        service.browse(0, 20);

        service.delete(created.id());

        assertThat(redisKeys.keys("products*"))
                .doesNotContain("products:item::" + created.id(), "products:list::0:20");
        assertThatThrownBy(() -> service.findById(created.id())).isInstanceOf(ProductNotFoundException.class);
    }

    @Test
    void createEvictsListPages() {
        long before = products.count();
        service.browse(0, 20);
        assertThat(redisKeys.keys("products*")).contains("products:list::0:20");

        service.create(new ProductRequest("Cache create test", new BigDecimal("12.00"), 1L));

        assertThat(redisKeys.keys("products*")).doesNotContain("products:list::0:20");
        assertThat(service.browse(0, 20).page().totalElements()).isEqualTo(before + 1);
    }
}
