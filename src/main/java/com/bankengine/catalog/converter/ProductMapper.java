package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.CreateProductRequestDto;
import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.dto.UpdateProductRequestDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ProductTypeMapper.class, FeatureLinkMapper.class, PricingLinkMapper.class})
public interface ProductMapper {

    @Mapping(target = "productType", source = "product.productType")
    @Mapping(target = "features", source = "product.productFeatureLinks")
    @Mapping(target = "pricing", source = "product.productPricingLinks")
    ProductResponseDto toResponseDto(Product product);

    List<ProductResponseDto> toResponseDtoList(List<Product> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productType", source = "productType")
    @Mapping(target = "name", source = "dto.name")
    @Mapping(target = "bankId", source = "dto.bankId")
    @Mapping(target = "effectiveDate", source = "dto.effectiveDate")
    @Mapping(target = "expirationDate", source = "dto.expirationDate")
    @Mapping(target = "status", source = "dto.status")
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Product toEntity(CreateProductRequestDto dto, ProductType productType);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "productType", ignore = true)
    @Mapping(target = "productFeatureLinks", ignore = true)
    @Mapping(target = "productPricingLinks", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "effectiveDate", ignore = true)
    @Mapping(target = "expirationDate", ignore = true)
    @Mapping(target = "status", ignore = true)
    void updateFromDto(UpdateProductRequestDto dto, @MappingTarget Product product);
}