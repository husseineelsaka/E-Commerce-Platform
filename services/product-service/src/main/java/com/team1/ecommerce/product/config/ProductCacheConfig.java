package com.team1.ecommerce.product.config;

import com.team1.ecommerce.product.dto.ProductPage;
import com.team1.ecommerce.product.dto.ProductView;
import java.time.Duration;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;

@Configuration
public class ProductCacheConfig implements CachingConfigurer {
    private static final Logger logger = LoggerFactory.getLogger(ProductCacheConfig.class);

    @Bean
    RedisCacheManager cacheManager(RedisConnectionFactory connections,
                                   @Value("${product.cache.ttl:10m}") Duration ttl) {
        RedisCacheConfiguration configuration = RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(ttl);
        RedisCacheConfiguration itemCache = configuration.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(ProductView.class)));
        RedisCacheConfiguration listCache = configuration.serializeValuesWith(
                RedisSerializationContext.SerializationPair.fromSerializer(
                        new Jackson2JsonRedisSerializer<>(ProductPage.class)));
        return RedisCacheManager.builder(connections).withInitialCacheConfigurations(Map.of(
                "products:item", itemCache, "products:list", listCache)).build();
    }

    @Bean
    @Override
    public CacheErrorHandler errorHandler() {
        return new CacheErrorHandler() {
            // ponytail: log and use PostgreSQL during Redis outages; add circuit breaking if repeated failures cost too much.
            @Override
            public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Redis cache read failed for {} key {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
                logger.warn("Redis cache write failed for {} key {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
                logger.warn("Redis cache eviction failed for {} key {}", cache.getName(), key, exception);
            }

            @Override
            public void handleCacheClearError(RuntimeException exception, Cache cache) {
                logger.warn("Redis cache clear failed for {}", cache.getName(), exception);
            }
        };
    }
}
