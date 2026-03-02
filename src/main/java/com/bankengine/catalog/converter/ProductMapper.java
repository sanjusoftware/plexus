package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductCatalogCard;
import com.bankengine.catalog.dto.ProductRequest;
import com.bankengine.catalog.dto.ProductResponse;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.mapping.ToNewEntity;
import com.bankengine.common.mapping.ToVersionableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;

import java.util.List;

@Mapper(config = MapStructConfig.class,
        componentModel = "spring",
        uses = {ProductTypeMapper.class, FeatureLinkMapper.class, PricingLinkMapper.class})
public interface ProductMapper {

    @Mapping(target = "features", source = "productFeatureLinks")
    @Mapping(target = "pricing", source = "productPricingLinks")
    ProductResponse toResponse(Product product);

    List<ProductResponse> toResponseList(List<Product> entities);

    @ToNewEntity
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "productType", source = "productType")
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    Product toEntity(ProductRequest dto, ProductType productType);

    @ToVersionableEntity
    @Mapping(target = "status", ignore = true)
    @Mapping(target = "productType", ignore = true)
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    void updateFromDto(ProductRequest dto, @MappingTarget Product product);

    @ToNewEntity
    @Mapping(target = "productType", source = "oldProduct.productType")
    @Mapping(target = "name", source = "requestDto.newName", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "code", source = "requestDto.newCode", nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE)
    @Mapping(target = "activationDate", source = "requestDto.newActivationDate")
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "bundleLinks", ignore = true)
    @Mapping(target = "tagline", source = "oldProduct.tagline")
    @Mapping(target = "fullDescription", source = "oldProduct.fullDescription")
    @Mapping(target = "iconUrl", source = "oldProduct.iconUrl")
    Product createNewVersionFrom(Product oldProduct, VersionRequest requestDto);

    @Mapping(target = "productId", source = "id")
    @Mapping(target = "productName", source = "name")
    @Mapping(target = "productTypeDisplayName", source = "productType.name")
    @Mapping(target = "keyFeatures", ignore = true)
    @Mapping(target = "pricingSummary", ignore = true)
    @Mapping(target = "eligibleForCustomer", ignore = true)
    @Mapping(target = "eligibilityMessage", ignore = true)
    ProductCatalogCard toCatalogCard(Product product);
}