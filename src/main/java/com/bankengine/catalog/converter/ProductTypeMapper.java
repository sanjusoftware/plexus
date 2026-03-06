package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;

import java.util.List;

@Mapper(config = MapStructConfig.class, componentModel = "spring")
public interface ProductTypeMapper {

    ProductTypeDto toResponse(ProductType entity);
    List<ProductTypeDto> toResponseList(List<ProductType> entities);

    @ToAuditableEntity
    @Mapping(target = "code", source = "code")
    ProductType toEntity(ProductTypeDto dto);

    @ToAuditableEntity
    @Mapping(target = "code", source = "code")
    void updateFromDto(ProductTypeDto dto, @MappingTarget ProductType entity);
}