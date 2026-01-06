package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductPricing;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.model.ProductPricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface PricingLinkMapper {

    /**
     * Converts a Pricing Link entity to a Response DTO for API output.
     * Maps the nested PricingComponent name to the flat pricingComponentName field.
     */
    @Mapping(target = "pricingComponentName", source = "link.pricingComponent.name")
    @Mapping(target = "context", source = "link.context")
    ProductPricing toResponse(ProductPricingLink link);

    List<ProductPricing> toResponseList(List<ProductPricingLink> links);

    /**
     * Used for deep-cloning pricing links during product versioning.
     * Ensures bank identity is preserved across versions.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", source = "oldLink.bankId")
    ProductPricingLink clone(ProductPricingLink oldLink);

    /**
     * Maps a Request DTO to a new Entity.
     * Relational and operational fields are ignored to be handled by the Service layer.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", ignore = true)
    ProductPricingLink toEntity(ProductPricing dto);

    /**
     * Updates an existing pricing link from a Request DTO.
     */
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "product", ignore = true)
    @Mapping(target = "pricingComponent", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "bankId", ignore = true)
    void updateFromDto(ProductPricing dto, @MappingTarget ProductPricingLink entity);
}