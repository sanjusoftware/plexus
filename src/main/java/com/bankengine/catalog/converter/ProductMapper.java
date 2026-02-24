package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.ProductVersionRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class,
        uses = {ProductTypeMapper.class, FeatureLinkMapper.class, PricingLinkMapper.class})
public interface ProductMapper {

    @Mapping(target = "features", source = "product.productFeatureLinks")
    @Mapping(target = "pricing", source = "product.productPricingLinks")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> entities);

    @ToAuditableEntity
    @Mapping(target = "productType", source = "productType")
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    @Mapping(target = "status", source = "dto.status", defaultValue = "DRAFT")
    Product toEntity(ProductRequest dto, ProductType productType);

    @ToAuditableEntity
    @Mapping(target = "productType", ignore = true)
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    void updateFromDto(ProductRequest dto, @MappingTarget Product product);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productType", source = "oldProduct.productType")
    @Mapping(target = "name", source = "requestDto.newName")
    @Mapping(target = "effectiveDate", source = "requestDto.newEffectiveDate")
    @Mapping(target = "status", constant = "DRAFT")
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "expirationDate", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    Product createNewVersionFrom(Product oldProduct, ProductVersionRequest requestDto);

    @Mapping(target = "productId", source = "id")
    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productTypeDisplayName", source = "productType.name")
    @Mapping(target = "keyFeatures", ignore = true)
    @Mapping(target = "pricingSummary", ignore = true)
    @Mapping(target = "eligibleForCustomer", ignore = true)
    @Mapping(target = "eligibilityMessage", ignore = true)
    ProductCatalogCard toCatalogCard(Product product);
}