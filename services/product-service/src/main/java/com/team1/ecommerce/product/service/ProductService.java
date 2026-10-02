package com.team1.ecommerce.product.service;

import com.team1.ecommerce.product.dto.ProductPage;
import com.team1.ecommerce.product.dto.ProductRequest;
import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.entity.Category;
import com.team1.ecommerce.product.entity.Product;
import com.team1.ecommerce.product.exception.CategoryNotFoundException;
import com.team1.ecommerce.product.exception.InvalidPagingException;
import com.team1.ecommerce.product.exception.ProductNotFoundException;
import com.team1.ecommerce.product.repository.CategoryRepository;
import com.team1.ecommerce.product.repository.ProductRepository;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;
    private final CategoryRepository categories;
    private final CacheManager cacheManager;
    private final CacheErrorHandler cacheErrors;

    public ProductService(ProductRepository products, CategoryRepository categories,
                          CacheManager cacheManager, CacheErrorHandler cacheErrors) {
        this.products = products;
        this.categories = categories;
        this.cacheManager = cacheManager;
        this.cacheErrors = cacheErrors;
    }

    @Cacheable(cacheNames = "products:list", key = "#page + ':' + #size")
    public ProductPage browse(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPagingException();
        }
        return ProductPage.from(products.browse(PageRequest.of(page, size, Sort.by("id"))));
    }

    @Cacheable(cacheNames = "products:item", key = "#id")
    public ProductView findById(Long id) {
        return products.findViewById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }

    @Transactional
    public ProductView create(ProductRequest request) {
        Category category = category(request.categoryId());
        Product product = products.save(new Product(category, request.name(), request.price()));
        evictAfterCommit(null);
        return view(product);
    }

    @Transactional
    public ProductView update(Long id, ProductRequest request) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.update(category(request.categoryId()), request.name(), request.price());
        evictAfterCommit(id);
        return view(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        products.delete(product);
        evictAfterCommit(id);
    }

    /** Evicts the product (and every cached list page) once the current transaction commits. */
    void evictAfterCommit(Long id) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            evictWrittenProduct(id);
            return;
        }
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                evictWrittenProduct(id);
            }
        });
    }

    private void evictWrittenProduct(Long id) {
        if (id != null) {
            evict("products:item", id);
        }
        clear("products:list");
    }

    private void evict(String name, Object key) {
        Cache cache = cacheManager.getCache(name);
        try {
            cache.evict(key);
        } catch (RuntimeException exception) {
            cacheErrors.handleCacheEvictError(exception, cache, key);
        }
    }

    private void clear(String name) {
        Cache cache = cacheManager.getCache(name);
        try {
            cache.clear();
        } catch (RuntimeException exception) {
            cacheErrors.handleCacheClearError(exception, cache);
        }
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private ProductView view(Product product) {
        return products.findViewById(product.getId()).orElseThrow(() -> new ProductNotFoundException(product.getId()));
    }
}
