package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.PricingMetadataDto;
import com.bankengine.pricing.model.PricingInputMetadata;
import com.bankengine.pricing.repository.PricingInputMetadataRepository;
import com.bankengine.pricing.repository.TierConditionRepository;
import com.bankengine.rules.service.KieContainerReloadService;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class PricingInputMetadataService {

    private final PricingInputMetadataRepository pricingInputMetadataRepository;
    private final TierConditionRepository tierConditionRepository;
    private final PricingInputMetadataMapper mapper;
    private final KieContainerReloadService reloadService;

    private static final String NOT_FOUND_MESSAGE = "Pricing Input Metadata not found with key: ";

    /**
     * Manual Constructor to properly apply @Lazy to the parameter that closes the cycle.
     */
    @Autowired
    public PricingInputMetadataService(
            PricingInputMetadataRepository pricingInputMetadataRepository,
            TierConditionRepository tierConditionRepository,
            PricingInputMetadataMapper mapper,
            @Lazy KieContainerReloadService reloadService
    ) {
        this.pricingInputMetadataRepository = pricingInputMetadataRepository;
        this.tierConditionRepository = tierConditionRepository;
        this.mapper = mapper;
        this.reloadService = reloadService;
    }

    /**
     * Retrieves metadata for a single attribute, using the cache if available.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "pricingMetadata", key = "#attributeKey")
    public PricingInputMetadata getMetadataEntityByKey(String attributeKey) {
        return pricingInputMetadataRepository.findByAttributeKey(attributeKey)
                .orElseThrow(() -> new IllegalArgumentException(
                        String.format("Invalid rule attribute '%s'. Not found in PricingInputMetadata registry.", attributeKey)));
    }

    /**
     * Retrieves a list of metadata entities for a set of attribute keys, using the cache
     * for bulk loading.
     */
    @Transactional(readOnly = true)
    public List<PricingInputMetadata> getMetadataEntitiesByKeys(Set<String> attributeKeys) {
        return pricingInputMetadataRepository.findByAttributeKeyIn(attributeKeys);
    }

    @Transactional(readOnly = true)
    public List<PricingMetadataDto> findAllMetadata() {
        return pricingInputMetadataRepository.findAll().stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PricingMetadataDto getMetadataByKey(String attributeKey) {
        return pricingInputMetadataRepository.findByAttributeKey(attributeKey)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));
    }

    @Transactional
    public PricingMetadataDto createMetadata(PricingMetadataDto dto) {
        // Business Rule: Key must be unique. Check before attempting save.
        if (pricingInputMetadataRepository.findByAttributeKey(dto.getAttributeKey()).isPresent()) {
            throw new DependencyViolationException(
                    "Cannot create Pricing Input Metadata: An attribute with the key '" + dto.getAttributeKey() + "' already exists."
            );
        }

        // Map DTO to Entity and save
        PricingInputMetadata entity = mapper.toEntity(dto);
        PricingInputMetadata savedEntity = pricingInputMetadataRepository.save(entity);

        reloadService.reloadKieContainer();

        return mapper.toResponse(savedEntity);
    }

    @Transactional
    public PricingMetadataDto updateMetadata(String attributeKey, PricingMetadataDto dto) {
        PricingInputMetadata entity = pricingInputMetadataRepository.findByAttributeKey(attributeKey)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));

        entity.setDisplayName(dto.getDisplayName());
        entity.setDataType(dto.getDataType());

        PricingInputMetadata updatedEntity = pricingInputMetadataRepository.save(entity);
        reloadService.reloadKieContainer();

        return mapper.toResponse(updatedEntity);
    }

    @Transactional
    public void deleteMetadata(String attributeKey) {
        PricingInputMetadata entity = pricingInputMetadataRepository.findByAttributeKey(attributeKey)
                .orElseThrow(() -> new NotFoundException(NOT_FOUND_MESSAGE + attributeKey));

        if (tierConditionRepository.existsByAttributeName(entity.getAttributeKey())) {
            throw new DependencyViolationException(
                    "Cannot delete Pricing Input Metadata '" + entity.getAttributeKey() +
                    "': It is used in one or more active tier conditions."
            );
        }

        pricingInputMetadataRepository.delete(entity);
        reloadService.reloadKieContainer();
    }
}
