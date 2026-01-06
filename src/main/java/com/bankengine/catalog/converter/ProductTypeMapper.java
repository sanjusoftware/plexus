package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProductTypeMapper {

    ProductTypeDto toResponse(ProductType entity);
    List<ProductTypeDto> toResponseList(List<ProductType> entities);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    ProductType toEntity(ProductTypeDto dto);
}