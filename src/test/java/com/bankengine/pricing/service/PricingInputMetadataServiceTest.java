package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.PricingMetadataDto;
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
        PricingInputMetadata entity = new PricingInputMetadata();
        when(pricingInputMetadataRepository.findByAttributeKey(key)).thenReturn(Optional.of(entity));

        PricingInputMetadata result = metadataService.getMetadataEntityByKey(key);

        assertThat(result).isEqualTo(entity);
    }

    @Test
    void getMetadataEntityByKey_ShouldThrowIllegalArgument_WhenMissing() {
        when(pricingInputMetadataRepository.findByAttributeKey("invalid")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> metadataService.getMetadataEntityByKey("invalid"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Not found in PricingInputMetadata registry");
    }

    @Test
    void createMetadata_ShouldSaveAndReload_WhenKeyIsUnique() {
        PricingMetadataDto dto = new PricingMetadataDto();
        dto.setAttributeKey("new_key");
        PricingInputMetadata entity = new PricingInputMetadata();

        when(pricingInputMetadataRepository.findByAttributeKey("new_key")).thenReturn(Optional.empty());
        when(mapper.toEntity(dto)).thenReturn(entity);
        when(pricingInputMetadataRepository.save(entity)).thenReturn(entity);

        metadataService.createMetadata(dto);

        verify(pricingInputMetadataRepository).save(entity);
        verify(reloadService).reloadKieContainer();
        verify(mapper).toResponse(any());
    }

    @Test
    void createMetadata_ShouldThrowException_WhenKeyAlreadyExists() {
        PricingMetadataDto dto = new PricingMetadataDto();
        dto.setAttributeKey("existing");
        when(pricingInputMetadataRepository.findByAttributeKey("existing")).thenReturn(Optional.of(new PricingInputMetadata()));

        assertThatThrownBy(() -> metadataService.createMetadata(dto))
                .isInstanceOf(DependencyViolationException.class)
                .hasMessageContaining("already exists");

        verify(reloadService, never()).reloadKieContainer();
    }

    @Test
    void updateMetadata_ShouldUpdateAndReload_WhenExists() {
        String key = "target";
        PricingMetadataDto dto = new PricingMetadataDto();
        dto.setDisplayName("New Display");
        PricingInputMetadata existing = new PricingInputMetadata();

        when(pricingInputMetadataRepository.findByAttributeKey(key)).thenReturn(Optional.of(existing));
        when(pricingInputMetadataRepository.save(existing)).thenReturn(existing);

        metadataService.updateMetadata(key, dto);

        assertThat(existing.getDisplayName()).isEqualTo("New Display");
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void deleteMetadata_ShouldDeleteAndReload_WhenNotUsedInConditions() {
        String key = "unused_key";
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey(key);

        when(pricingInputMetadataRepository.findByAttributeKey(key)).thenReturn(Optional.of(entity));
        when(tierConditionRepository.existsByAttributeName(key)).thenReturn(false);

        metadataService.deleteMetadata(key);

        verify(pricingInputMetadataRepository).deleteByAttributeKey(key);
        verify(reloadService).reloadKieContainer();
    }

    @Test
    void deleteMetadata_ShouldThrowException_WhenUsedInConditions() {
        String key = "active_key";
        PricingInputMetadata entity = new PricingInputMetadata();
        entity.setAttributeKey(key);

        when(pricingInputMetadataRepository.findByAttributeKey(key)).thenReturn(Optional.of(entity));
        when(tierConditionRepository.existsByAttributeName(key)).thenReturn(true);

        assertThatThrownBy(() -> metadataService.deleteMetadata(key))
                .isInstanceOf(DependencyViolationException.class)
                .hasMessageContaining("used in one or more active tier conditions");

        verify(pricingInputMetadataRepository, never()).deleteByAttributeKey(any());
        verify(reloadService, never()).reloadKieContainer();
    }

    @Test
    void getMetadataEntitiesByKeys_ShouldDelegateToRepository() {
        Set<String> keys = Set.of("a", "b");
        metadataService.getMetadataEntitiesByKeys(keys);
        verify(pricingInputMetadataRepository).findByAttributeKeyIn(keys);
    }
}