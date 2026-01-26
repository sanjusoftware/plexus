package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.ProductMapper;
import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.pricing.dto.ProductPricingCalculationResult;
import com.bankengine.pricing.service.PricingCalculationService;
import com.bankengine.test.config.BaseServiceTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class PublicCatalogServiceTest extends BaseServiceTest {

    @Mock private ProductRepository productRepository;
    @Mock private ProductMapper productMapper;
    @Mock private PricingCalculationService pricingCalculationService;

    @InjectMocks private PublicCatalogService publicCatalogService;

    @Test
    @DisplayName("Recommendations - Should personalize prices and sort by cheapest")
    void testGetRecommendedProducts() {
        Product p1 = new Product(); p1.setId(101L);
        Product p2 = new Product(); p2.setId(102L);

        when(productRepository.findAll(any(Specification.class))).thenReturn(List.of(p1, p2));

        // Mocking Mapper to provide empty DTOs with builders
        when(productMapper.toCatalogCard(any())).thenAnswer(inv -> {
            return ProductCatalogCard.builder()
                    .pricingSummary(ProductCatalogCard.PricingSummary.builder().build())
                    .build();
        });

        // Mocking Calculation: P2 ($5) is cheaper than P1 ($20)
        when(pricingCalculationService.getProductPricing(any())).thenAnswer(inv -> {
            var req = (com.bankengine.pricing.dto.PricingRequest) inv.getArgument(0);
            BigDecimal price = req.getProductId().equals(101L) ? new BigDecimal("20.00") : new BigDecimal("5.00");
            return ProductPricingCalculationResult.builder().finalChargeablePrice(price).build();
        });

        List<ProductCatalogCard> results = publicCatalogService.getRecommendedProducts("RETAIL", new BigDecimal("5000"));

        assertEquals(2, results.size());
        assertEquals(new BigDecimal("5.00"), results.get(0).getPricingSummary().getMainPriceValue(), "Cheapest product should be first");
    }
}