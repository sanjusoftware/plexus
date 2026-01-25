package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.config.MapStructConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

import java.util.List;

@Mapper(config = MapStructConfig.class, uses = {ProductMapper.class, BundlePricingMapper.class})
public interface ProductBundleMapper {

    @Mapping(target = "items", source = "containedProducts")
    @Mapping(target = "pricing", source = "bundlePricingLinks")
    ProductBundleResponse toResponse(ProductBundle bundle);

    List<ProductBundleResponse> toResponseList(List<ProductBundle> bundles);

    @Mapping(target = "product", source = "product")
    ProductBundleResponse.BundleItemResponse toItemResponse(BundleProductLink link);
}
