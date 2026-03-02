package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.common.mapping.ToNewEntity;
import com.bankengine.common.mapping.ToVersionableEntity;
import com.bankengine.config.MapStructConfig;
import com.bankengine.pricing.model.BundlePricingLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface ProductBundleMapper {

    @Mapping(target = "products", source = "containedProducts")
    @Mapping(target = "pricing", source = "bundlePricingLinks")
    ProductBundleResponse toResponse(ProductBundle entity);

    @ToNewEntity
    @Mapping(target = "containedProducts", ignore = true)
    @Mapping(target = "bundlePricingLinks", ignore = true)
    ProductBundle toEntity(ProductBundleRequest dto);

    @ToVersionableEntity
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "containedProducts", ignore = true)
    @Mapping(target = "bundlePricingLinks", ignore = true)
    void updateFromDto(ProductBundleRequest dto, @MappingTarget ProductBundle entity);

    @ToNewEntity
    @Mapping(target = "containedProducts", ignore = true)
    @Mapping(target = "bundlePricingLinks", ignore = true)
    @Mapping(target = "activationDate", ignore = true) // Clear dates for new bundle version
    @Mapping(target = "expiryDate", ignore = true)
    ProductBundle clone(ProductBundle oldBundle);

    @Mapping(target = "productId", source = "product.id")
    @Mapping(target = "productName", source = "product.name")
    @Mapping(target = "productCode", source = "product.code")
    ProductBundleResponse.BundleProductDto toBundleProductDto(BundleProductLink link);

    @Mapping(target = "pricingComponentId", source = "pricingComponent.id")
    @Mapping(target = "pricingComponentName", source = "pricingComponent.name")
    @Mapping(target = "targetComponentCode", source = "pricingComponent.code")
    ProductBundleResponse.ProductPricing toPricingDto(BundlePricingLink link);

    @ToAuditableEntity
    @Mapping(target = "productBundle", ignore = true)
    @Mapping(target = "product", ignore = true)
    BundleProductLink toLink(ProductBundleRequest.BundleProduct dto);

    /**
     * Clones a pricing link for a bundle versioning event.
     * Crucially ignores dates so that pricing must be re-activated.
     */
    @ToAuditableEntity
    @Mapping(target = "productBundle", ignore = true)
    @Mapping(target = "pricingComponent", source = "oldLink.pricingComponent")
    @Mapping(target = "effectiveDate", ignore = true) // Principle: New version = Dormant pricing
    @Mapping(target = "expiryDate", ignore = true)
    BundlePricingLink clonePricing(BundlePricingLink oldLink);
}