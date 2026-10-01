package com.team1.ecommerce.product.service;

import com.team1.ecommerce.product.dto.ProductRequest;
import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.entity.Category;
import com.team1.ecommerce.product.entity.Product;
import com.team1.ecommerce.product.exception.CategoryNotFoundException;
import com.team1.ecommerce.product.exception.InvalidPagingException;
import com.team1.ecommerce.product.exception.ProductNotFoundException;
import com.team1.ecommerce.product.repository.CategoryRepository;
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
    private final CategoryRepository categories;

    public ProductService(ProductRepository products, CategoryRepository categories) {
        this.products = products;
        this.categories = categories;
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

    @Transactional
    public ProductView create(ProductRequest request) {
        Category category = category(request.categoryId());
        Product product = products.save(new Product(category, request.name(), request.price()));
        return view(product);
    }

    @Transactional
    public ProductView update(Long id, ProductRequest request) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        product.update(category(request.categoryId()), request.name(), request.price());
        return view(product);
    }

    @Transactional
    public void delete(Long id) {
        Product product = products.findById(id).orElseThrow(() -> new ProductNotFoundException(id));
        products.delete(product);
    }

    private Category category(Long id) {
        return categories.findById(id).orElseThrow(() -> new CategoryNotFoundException(id));
    }

    private ProductView view(Product product) {
        Category category = product.getCategory();
        return new ProductView(product.getId(), product.getName(), product.getPrice(),
                category.getId(), category.getName());
    }
}
