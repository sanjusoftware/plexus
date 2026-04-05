package com.bankengine.pricing;

import com.bankengine.pricing.model.PricingInputMetadata;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializer;
import org.springframework.security.jackson2.SecurityJackson2Modules;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PricingInputMetadataRedisSerializationTest {

    @Test
    void shouldDeserializeLegacyRedisPayloadContainingComputedFields() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModules(SecurityJackson2Modules.getModules(getClass().getClassLoader()));
        mapper.activateDefaultTyping(
                mapper.getPolymorphicTypeValidator(),
                ObjectMapper.DefaultTyping.NON_FINAL
        );

        RedisSerializer<Object> serializer = new GenericJackson2JsonRedisSerializer(mapper);

        PricingInputMetadata metadata = PricingInputMetadata.builder()
                .id(1L)
                .attributeKey("customer_segment")
                .dataType("STRING")
                .displayName("Customer Segment")
                .build();

        byte[] serialized = serializer.serialize(metadata);
        assertNotNull(serialized);

        JsonNode root = mapper.readTree(new String(serialized, StandardCharsets.UTF_8));
        assertTrue(root.isArray(), "Expected GenericJackson2JsonRedisSerializer to write type metadata as a JSON array");
        assertTrue(root.get(1).isObject(), "Expected payload body to be a JSON object");

        ObjectNode payload = (ObjectNode) root.get(1);
        payload.put("fqnType", "java.lang.String");
        payload.put("needsQuotes", true);

        Object deserialized = serializer.deserialize(mapper.writeValueAsBytes(root));

        assertInstanceOf(PricingInputMetadata.class, deserialized);
        PricingInputMetadata restored = (PricingInputMetadata) deserialized;
        assertEquals("customer_segment", restored.getAttributeKey());
        assertEquals("STRING", restored.getDataType());
        assertEquals("Customer Segment", restored.getDisplayName());
    }
}


