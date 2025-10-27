package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductResponseDto;
import com.bankengine.catalog.model.Product;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING,
        uses = {ProductTypeMapper.class, FeatureLinkMapper.class, PricingLinkMapper.class})
public interface ProductMapper {

    @Mapping(target = "productType", source = "product.productType")
    @Mapping(target = "features", source = "product.productFeatureLinks")
    @Mapping(target = "pricing", source = "product.productPricingLinks")
    ProductResponseDto toResponseDto(Product product);

    List<ProductResponseDto> toResponseDtoList(List<Product> entities);
}