package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeRequest;
import com.bankengine.catalog.dto.ProductTypeResponse;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProductTypeMapper {

    ProductTypeResponse toResponseDto(ProductType entity);
    List<ProductTypeResponse> toResponseDtoList(List<ProductType> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductType toEntity(ProductTypeRequest dto);
}