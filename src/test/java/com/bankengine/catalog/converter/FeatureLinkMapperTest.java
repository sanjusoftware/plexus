package com.bankengine.catalog.converter;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.model.ProductFeatureLink;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import static org.assertj.core.api.Assertions.assertThat;

public class FeatureLinkMapperTest {

    private final FeatureLinkMapper mapper = Mappers.getMapper(FeatureLinkMapper.class);

    @Test
    void shouldCorrectlyCloneFeatureLink() {
        // Arrange
        FeatureComponent featureComponent = new FeatureComponent();
        featureComponent.setName("Test Feature");

        ProductFeatureLink oldLink = new ProductFeatureLink();
        oldLink.setFeatureComponent(featureComponent);
        oldLink.setFeatureValue("Test Value");

        // Act
        ProductFeatureLink newLink = mapper.clone(oldLink);

        // Assert
        assertThat(newLink).isNotNull();
        assertThat(newLink.getId()).isNull();
        assertThat(newLink.getFeatureComponent().getName()).isEqualTo("Test Feature");
        assertThat(newLink.getFeatureValue()).isEqualTo("Test Value");
    }
}
