package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductTypeDto;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.common.mapping.ToAuditableEntity;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(config = MapStructConfig.class)
public interface ProductTypeMapper {
    ProductTypeDto toResponse(ProductType entity);
    List<ProductTypeDto> toResponseList(List<ProductType> entities);

    @ToAuditableEntity
    ProductType toEntity(ProductTypeDto dto);
}