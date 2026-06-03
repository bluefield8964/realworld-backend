package realworld_backend.commerce.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import realworld_backend.commerce.model.Product;
import realworld_backend.commerce.repository.ProductRepository;
import realworld_backend.common.exception.BizException;
import realworld_backend.common.exception.ErrorCode;

@Service
@RequiredArgsConstructor
public class ProductItemService {
    private final ProductRepository productRepository;

    public Product findByProductId(Long productId) {
        return productRepository.findById(productId)
                .orElseThrow(() -> new BizException(ErrorCode.INVALID_INPUT));
    }
}

