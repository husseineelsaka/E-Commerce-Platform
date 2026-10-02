package com.team1.ecommerce.product.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.team1.ecommerce.product.dto.ProductPage;
import com.team1.ecommerce.product.dto.ProductView;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.cache.RedisCache;
import org.springframework.data.redis.connection.RedisConnectionFactory;

class ProductCacheConfigTest {
    @Test
    void cacheValuesRoundTripAsReadableJson() {
        var manager = new ProductCacheConfig().cacheManager(mock(RedisConnectionFactory.class),
                Duration.ofMinutes(10));
        manager.afterPropertiesSet();
        var product = new ProductView(1L, "Wireless Headphones", new BigDecimal("79.99"), 1L, "Electronics",
                new BigDecimal("4.50"), 2);
        var page = new ProductPage(List.of(product), new ProductPage.PageMetadata(20, 0, 1, 1));

        assertRoundTrip((RedisCache) manager.getCache("products:item"), product);
        assertRoundTrip((RedisCache) manager.getCache("products:list"), page);
    }

    private void assertRoundTrip(RedisCache cache, Object original) {
        var serializer = cache.getCacheConfiguration().getValueSerializationPair();
        ByteBuffer encoded = serializer.write(original);
        assertThat(StandardCharsets.UTF_8.decode(encoded.duplicate()).toString())
                .contains("\"categoryName\":\"Electronics\"")
                .doesNotContain("@class");
        assertThat(serializer.read(encoded)).isEqualTo(original);
        assertThat(cache.getCacheConfiguration().getTtl()).isEqualTo(Duration.ofMinutes(10));
    }
}
