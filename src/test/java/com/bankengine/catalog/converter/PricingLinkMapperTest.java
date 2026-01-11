package com.bankengine.catalog.converter;

import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

public class PricingLinkMapperTest {

    private final PricingLinkMapper mapper = Mappers.getMapper(PricingLinkMapper.class);

    @Test
    void shouldCorrectlyClonePricingLink() {
        // Arrange
        PricingComponent pricingComponent = new PricingComponent();
        pricingComponent.setName("Test Pricing");

        ProductPricingLink oldLink = new ProductPricingLink();
        oldLink.setPricingComponent(pricingComponent);

        // Act
        ProductPricingLink newLink = mapper.clone(oldLink);

        // Assert
        assertThat(newLink).isNotNull();
        assertThat(newLink.getId()).isNull();
        assertThat(newLink.getPricingComponent().getName()).isEqualTo("Test Pricing");
    }
}
