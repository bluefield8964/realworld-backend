package realworld_backend.commerce.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import realworld_backend.commerce.model.Product;

@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {



}

