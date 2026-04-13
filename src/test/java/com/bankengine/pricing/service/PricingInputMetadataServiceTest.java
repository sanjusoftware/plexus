package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.PricingMetadataRequest;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PricingInputMetadataServiceTest extends BaseServiceTest {

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
        entity.setAttributeKey("new_key");
        PricingInputMetadata saved = new PricingInputMetadata();
        saved.setAttributeKey("new_key");

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, "new_key")).thenReturn(Optional.empty());
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(pricingInputMetadataRepository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(null);

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
        PricingInputMetadata saved = new PricingInputMetadata();
        saved.setAttributeKey("new_key_case");

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, "new_key_case")).thenReturn(Optional.empty());
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(pricingInputMetadataRepository.save(entity)).thenReturn(saved);
        when(mapper.toResponse(saved)).thenReturn(null);

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
        dto.setDataType("STRING");
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, "existing")).thenReturn(Optional.of(new PricingInputMetadata()));

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

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, key)).thenReturn(Optional.of(existing));
        when(pricingInputMetadataRepository.save(existing)).thenReturn(existing);
        when(mapper.toResponse(existing)).thenReturn(null);

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

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, key)).thenReturn(Optional.of(entity));
        when(tierConditionRepository.existsByAttributeName(key)).thenReturn(false);

        metadataService.deleteMetadata(key);

        verify(pricingInputMetadataRepository).deleteByBankIdAndAttributeKey(TEST_BANK_ID, key);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void deleteMetadata_ShouldThrowException_WhenUsedInConditions() {
        String key = "active_key";
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey(key);
        entity.setBankId(TEST_BANK_ID);
        entity.setDataType("STRING");

        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, key)).thenReturn(Optional.of(entity));
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
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKeyIn(TEST_BANK_ID, keys)).thenReturn(List.of());
        metadataService.getMetadataEntitiesByKeys(keys);
        verify(pricingInputMetadataRepository).findByBankIdAndAttributeKeyIn(TEST_BANK_ID, keys);
    }

    @Test
    void updateMetadata_ShouldThrowNotFound_WhenMissing() {
        when(pricingInputMetadataRepository.findByBankIdAndAttributeKey(TEST_BANK_ID, "target")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metadataService.updateMetadata("target", new PricingMetadataRequest()))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Pricing Input Metadata not found with key: target");
    }
}
