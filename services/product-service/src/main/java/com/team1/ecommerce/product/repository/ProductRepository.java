package com.team1.ecommerce.product.repository;

import com.team1.ecommerce.product.dto.ProductView;
import com.team1.ecommerce.product.entity.Product;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProductRepository extends JpaRepository<Product, Long> {
    @Query(value = """
            select new com.team1.ecommerce.product.dto.ProductView(
                p.id, p.name, p.price, c.id, c.name, r.reviewCount, r.ratingSum)
            from Product p join p.category c left join ProductRating r on r.productId = p.id
            """, countQuery = "select count(p) from Product p")
    Page<ProductView> browse(Pageable pageable);

    @Query("""
            select new com.team1.ecommerce.product.dto.ProductView(
                p.id, p.name, p.price, c.id, c.name, r.reviewCount, r.ratingSum)
            from Product p join p.category c left join ProductRating r on r.productId = p.id where p.id = :id
            """)
    Optional<ProductView> findViewById(Long id);
}
