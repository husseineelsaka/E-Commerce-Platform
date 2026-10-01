package com.team1.ecommerce.product.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Version;
import java.math.BigDecimal;

@Entity
public class Product {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "category_id")
    private Category category;

    private String name;

    private BigDecimal price;

    @Version
    private Long version;

    protected Product() {
    }

    public Product(Category category, String name, BigDecimal price) {
        this.category = category;
        this.name = name;
        this.price = price;
    }

    public Long getId() {
        return id;
    }
}
