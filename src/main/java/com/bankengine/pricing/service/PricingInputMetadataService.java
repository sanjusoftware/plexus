package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.CreateMetadataRequestDto;
import com.bankengine.pricing.dto.MetadataResponseDto;
import com.bankengine.pricing.dto.UpdateMetadataRequestDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PricingInputMetadataService {

    private final PricingInputMetadataRepository repository;
    private final TierConditionRepository tierConditionRepository;
    private final PricingInputMetadataMapper mapper;
    private final KieContainerReloadService reloadService;

    private static final String NOT_FOUND_MESSAGE = "Pricing Input Metadata not found with key: ";

    @Transactional(readOnly = true)
    public List<MetadataResponseDto> findAllMetadata() {
        return repository.findAll().stream()
                .map(mapper::toResponseDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public MetadataResponseDto getMetadataByKey(String attributeKey) {
        return repository.findByAttributeKey(attributeKey)
                .map(mapper::toResponseDto)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));
    }

    // --- CREATE OPERATION ---

    @Transactional
    public MetadataResponseDto createMetadata(CreateMetadataRequestDto dto) {
        // Business Rule: Key must be unique. Check before attempting save.
        if (repository.findByAttributeKey(dto.getAttributeKey()).isPresent()) {
            throw new DependencyViolationException(
                    "Cannot create Pricing Input Metadata: An attribute with the key '" + dto.getAttributeKey() + "' already exists."
            );
        }

        // Map DTO to Entity and save
        PricingInputMetadata entity = mapper.toEntity(dto);
        PricingInputMetadata savedEntity = repository.save(entity);

        reloadService.reloadKieContainer();

        return mapper.toResponseDto(savedEntity);
    }

    // --- UPDATE OPERATION (Using attributeKey for lookup) ---

    @Transactional
    public MetadataResponseDto updateMetadata(String attributeKey, UpdateMetadataRequestDto dto) {
        PricingInputMetadata entity = repository.findByAttributeKey(attributeKey)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));

        entity.setDisplayName(dto.getDisplayName());
        entity.setDataType(dto.getDataType());

        PricingInputMetadata updatedEntity = repository.save(entity);
        reloadService.reloadKieContainer();

        return mapper.toResponseDto(updatedEntity);
    }

    // --- DELETE OPERATION ---

    @Transactional
    public void deleteMetadata(String attributeKey) {
        PricingInputMetadata entity = repository.findByAttributeKey(attributeKey)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));

        if (tierConditionRepository.existsByAttributeName(entity.getAttributeKey())) {
            throw new DependencyViolationException(
                    "Cannot delete Pricing Input Metadata '" + entity.getAttributeKey() +
                    "': It is used in one or more active tier conditions."
            );
        }

        repository.delete(entity);
        reloadService.reloadKieContainer();
    }
}
