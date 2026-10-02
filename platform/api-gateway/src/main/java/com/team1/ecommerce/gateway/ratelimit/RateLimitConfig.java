package com.team1.ecommerce.gateway.ratelimit;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cloud.gateway.filter.ratelimit.KeyResolver;
import org.springframework.cloud.gateway.filter.ratelimit.RateLimiter;
import org.springframework.cloud.gateway.filter.ratelimit.RedisRateLimiter;
import org.springframework.cloud.gateway.support.ConfigurationService;
import org.springframework.cloud.gateway.support.ipresolver.XForwardedRemoteAddressResolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.web.server.util.matcher.IpAddressServerWebExchangeMatcher;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

@Configuration
public class RateLimitConfig {
    @Bean
    @ConfigurationProperties(prefix = "gateway.rate-limit")
    Limits limits() {
        return new Limits();
    }

    @Bean
    KeyResolver clientKeyResolver(Limits limits) {
        var forwarded = XForwardedRemoteAddressResolver.maxTrustedIndex(1);
        return exchange -> exchange.getPrincipal().ofType(JwtAuthenticationToken.class)
                .map(jwt -> "user:" + jwt.getToken().getSubject())
                .switchIfEmpty(Mono.defer(() -> {
                    InetSocketAddress peer = exchange.getRequest().getRemoteAddress();
                    return Flux.fromIterable(limits.getTrustedProxies())
                            .concatMap(cidr -> new IpAddressServerWebExchangeMatcher(cidr).matches(exchange))
                            .any(match -> match.isMatch())
                            .map(trusted -> "ip:" + (trusted ? forwarded.resolve(exchange) : peer)
                                    .getAddress().getHostAddress());
                }));
    }

    @Bean
    @Primary
    RateLimiter<RedisRateLimiter.Config> clientRateLimiter(ReactiveStringRedisTemplate redisTemplate,
            ConfigurationService configurationService, Limits limits) {
        var redis = new RedisRateLimiter(redisTemplate, tokenBucketScript(), configurationService);
        redis.getConfig().put("anonymous", new RedisRateLimiter.Config()
                .setReplenishRate(limits.getAnonymous().getReplenishRate())
                .setBurstCapacity(limits.getAnonymous().getBurstCapacity()));
        redis.getConfig().put("signed-in", new RedisRateLimiter.Config()
                .setReplenishRate(limits.getSignedIn().getReplenishRate())
                .setBurstCapacity(limits.getSignedIn().getBurstCapacity()));
        // ponytail: Fail open during a Redis outage so browsing continues; use fail closed if abuse during outages becomes unacceptable.
        return new ClientRateLimiter(redis);
    }

    /**
     * Spring Cloud Gateway's token-bucket script, read once. The auto-configured script re-reads the Lua file's
     * last-modified time from the jar on every request (about 5 % of Gateway CPU in the L5 profile).
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static RedisScript<List<Long>> tokenBucketScript() {
        try {
            String lua = new ClassPathResource("META-INF/scripts/request_rate_limiter.lua")
                    .getContentAsString(StandardCharsets.UTF_8);
            return (RedisScript) RedisScript.of(lua, List.class);
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    public static class Limits {
        private Policy anonymous;
        private Policy signedIn;
        private List<String> trustedProxies = List.of();

        public Policy getAnonymous() { return anonymous; }
        public void setAnonymous(Policy anonymous) { this.anonymous = anonymous; }
        public Policy getSignedIn() { return signedIn; }
        public void setSignedIn(Policy signedIn) { this.signedIn = signedIn; }
        public List<String> getTrustedProxies() { return trustedProxies; }
        public void setTrustedProxies(List<String> trustedProxies) { this.trustedProxies = trustedProxies; }
    }

    public static class Policy {
        private int replenishRate;
        private int burstCapacity;

        public int getReplenishRate() { return replenishRate; }
        public void setReplenishRate(int replenishRate) { this.replenishRate = replenishRate; }
        public int getBurstCapacity() { return burstCapacity; }
        public void setBurstCapacity(int burstCapacity) { this.burstCapacity = burstCapacity; }
    }

    private record ClientRateLimiter(RedisRateLimiter redis) implements RateLimiter<RedisRateLimiter.Config> {
        @Override
        public Mono<Response> isAllowed(String routeId, String key) {
            return redis.isAllowed(key.startsWith("user:") ? "signed-in" : "anonymous", key);
        }

        @Override
        public Class<RedisRateLimiter.Config> getConfigClass() { return redis.getConfigClass(); }

        @Override
        public RedisRateLimiter.Config newConfig() { return redis.newConfig(); }

        @Override
        public Map<String, RedisRateLimiter.Config> getConfig() { return redis.getConfig(); }
    }
}
