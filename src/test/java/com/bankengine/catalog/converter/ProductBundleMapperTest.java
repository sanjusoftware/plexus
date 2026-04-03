package com.bankengine.catalog.converter;

import com.bankengine.catalog.dto.ProductBundleResponse;
import com.bankengine.catalog.model.ProductBundle;
import com.bankengine.common.model.VersionableEntity;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class ProductBundleMapperTest {

    private final ProductBundleMapper mapper = new ProductBundleMapperImpl();

    @Test
    void testToResponse_ShouldMapVersionAndStatus() {
        ProductBundle entity = ProductBundle.builder()
                .name("Starter Bundle")
                .code("STARTER")
                .version(5)
                .status(VersionableEntity.EntityStatus.ACTIVE)
                .targetCustomerSegments("RETAIL")
                .build();

        ProductBundleResponse response = mapper.toResponse(entity);

        assertNotNull(response);
        assertEquals("Starter Bundle", response.getName());
        assertEquals(5, response.getVersion());
        assertEquals("ACTIVE", response.getStatus());
    }
}

