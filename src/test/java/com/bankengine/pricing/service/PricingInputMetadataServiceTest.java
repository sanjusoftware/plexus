package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingInputMetadataServiceTest {

    @Mock private PricingInputMetadataRepository pricingInputMetadataRepository;
    @Mock private TierConditionRepository tierConditionRepository;
    @Mock private PricingInputMetadataMapper mapper;
    @Mock private KieContainerReloadService reloadService;

    @InjectMocks
    private PricingInputMetadataService metadataService;

    @Test
    void getMetadataEntityByKey_ShouldReturnEntity_WhenExists() {
        String key = "amount";
        String bankId = "BANK1";
        PricingInputMetadata entity = new PricingInputMetadata();
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(bankId, key)).thenReturn(Optional.of(entity));

        PricingInputMetadata result = metadataService.getMetadataEntityByKey(key, bankId);

        assertThat(result).isEqualTo(entity);
    }

    @Test
    void getMetadataEntityByKey_ShouldThrowIllegalArgument_WhenMissing() {
        String bankId = "BANK1";
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(bankId, "invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metadataService.getMetadataEntityByKey("invalid", bankId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not found in PricingInputMetadata registry");
    }

    @Test
    void createMetadata_ShouldSaveAndReload_WhenKeyIsUnique() {
        PricingMetadataRequest dto = new PricingMetadataRequest();
        dto.setAttributeKey("new_key");
        dto.setDisplayName("New Key");
        dto.setDataType("DECIMAL");
        dto.setSourceType("FACT_FIELD");
        dto.setSourceField("transactionAmount");
        PricingInputMetadata entity = new PricingInputMetadata();

        // Use anyString() because we don't control getCurrentBankId() in the mock unless we mock the context holder
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq("new_key"))).thenReturn(Optional.empty());
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(pricingInputMetadataRepository.save(entity)).thenReturn(entity);

        metadataService.createMetadata(dto);

        assertThat(dto.getSourceType()).isEqualTo("FACT_FIELD");
        assertThat(dto.getSourceField()).isEqualTo("transactionAmount");
        verify(pricingInputMetadataRepository).save(entity);
        verify(reloadService).reloadKieContainer();
        verify(mapper).toResponse(any());
    }

    @Test
    void createMetadata_ShouldNormalizeDataTypeCase_WhenLowercaseProvided() {
        PricingMetadataRequest dto = new PricingMetadataRequest();
        dto.setAttributeKey("new_key_case");
        dto.setDisplayName("New Key Case");
        dto.setDataType("string");
        dto.setSourceField("new_key_case");
        PricingInputMetadata entity = new PricingInputMetadata();

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq("new_key_case"))).thenReturn(Optional.empty());
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(pricingInputMetadataRepository.save(entity)).thenReturn(entity);

        metadataService.createMetadata(dto);

        assertThat(dto.getDataType()).isEqualTo("STRING");
        assertThat(dto.getSourceType()).isEqualTo("CUSTOM_ATTRIBUTE");
        verify(pricingInputMetadataRepository).save(entity);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void createMetadata_ShouldThrowException_WhenKeyAlreadyExists() {
        PricingMetadataRequest dto = new PricingMetadataRequest();
        dto.setAttributeKey("existing");
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq("existing"))).thenReturn(Optional.of(new PricingInputMetadata()));

        assertThatThrownBy(() -> metadataService.createMetadata(dto))
                .isInstanceOf(DependencyViolationException.class)
                .hasMessageContaining("already exists");

        verify(reloadService, never()).reloadKieContainer();
    }

    @Test
    void updateMetadata_ShouldUpdateAndReload_WhenExists() {
        String key = "target";
        PricingMetadataRequest dto = new PricingMetadataRequest();
        dto.setDisplayName("New Display");
        dto.setDataType("boolean");
        dto.setSourceType("FACT_FIELD");
        dto.setSourceField("customerSegment");
        PricingInputMetadata existing = new PricingInputMetadata();
        existing.setAttributeKey(key);

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq(key))).thenReturn(Optional.of(existing));
        when(pricingInputMetadataRepository.save(existing)).thenReturn(existing);

        metadataService.updateMetadata(key, dto);

        assertThat(existing.getDisplayName()).isEqualTo("New Display");
        assertThat(existing.getDataType()).isEqualTo("BOOLEAN");
        assertThat(existing.getSourceField()).isEqualTo("customerSegment");
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void deleteMetadata_ShouldDeleteAndReload_WhenNotUsedInConditions() {
        String key = "unused_key";
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey(key);

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq(key))).thenReturn(Optional.of(entity));
        when(tierConditionRepository.existsByAttributeName(key)).thenReturn(false);

        metadataService.deleteMetadata(key);

        verify(pricingInputMetadataRepository).deleteByBankIdAndAttributeKey(any(), eq(key));
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void deleteMetadata_ShouldThrowException_WhenUsedInConditions() {
        String key = "active_key";
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey(key);

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(any(), eq(key))).thenReturn(Optional.of(entity));
        when(tierConditionRepository.existsByAttributeName(key)).thenReturn(true);

        assertThatThrownBy(() -> metadataService.deleteMetadata(key))
                .isInstanceOf(DependencyViolationException.class)
                .hasMessageContaining("used in one or more active tier conditions");

        verify(pricingInputMetadataRepository, never()).deleteByBankIdAndAttributeKey(any(), any());
        verify(reloadService, never()).reloadKieContainer();
    }

    @Test
    void getMetadataEntitiesByKeys_ShouldDelegateToRepository() {
        Set<String> keys = Set.of("a", "b");
        metadataService.getMetadataEntitiesByKeys(keys);
        verify(pricingInputMetadataRepository).findByBankIdAndAttributeKeyIn(any(), eq(keys));
    }
}
