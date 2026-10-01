package com.team1.ecommerce.product.service;

import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.exception.ProductNotFoundException;
import com.team1.ecommerce.product.exception.InvalidPagingException;
import com.team1.ecommerce.product.repository.ProductRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class ProductService {
    private final ProductRepository products;

    public ProductService(ProductRepository products) {
        this.products = products;
    }

    public Page<ProductView> browse(int page, int size) {
        if (page < 0 || size < 1 || size > 100) {
            throw new InvalidPagingException();
        }
        return products.browse(PageRequest.of(page, size, Sort.by("id")));
    }

    public ProductView findById(Long id) {
        return products.findViewById(id).orElseThrow(() -> new ProductNotFoundException(id));
    }
}
