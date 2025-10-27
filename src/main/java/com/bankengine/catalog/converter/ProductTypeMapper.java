package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.CreateProductTypeRequestDto;
import com.bankengine.catalog.dto.ProductTypeResponseDto;
import com.bankengine.catalog.model.ProductType;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingConstants;

import java.util.List;

@Mapper(componentModel = MappingConstants.ComponentModel.SPRING)
public interface ProductTypeMapper {

    ProductTypeResponseDto toResponseDto(ProductType entity);
    List<ProductTypeResponseDto> toResponseDtoList(List<ProductType> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductType toEntity(CreateProductTypeRequestDto dto);
}