package com.bankengine.pricing.service;

import com.bankengine.pricing.converter.PricingInputMetadataMapper;
import com.bankengine.pricing.dto.PricingInputMetadataDto;
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

    @Transactional(readOnly = true)
    public List<PricingInputMetadataDto> findAll() {
        return repository.findAll().stream()
                .map(mapper::toDto)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public PricingInputMetadataDto findById(Long id) {
        return repository.findById(id)
                .map(mapper::toDto)
                .orElseThrow(() -> new NotFoundException("PricingInputMetadata not found with id: " + id));
    }

    @Transactional
    public PricingInputMetadataDto create(PricingInputMetadataDto dto) {
        PricingInputMetadata entity = mapper.toEntity(dto);
        PricingInputMetadata savedEntity = repository.save(entity);
        reloadService.reloadKieContainer();
        return mapper.toDto(savedEntity);
    }

    @Transactional
    public PricingInputMetadataDto update(Long id, PricingInputMetadataDto dto) {
        PricingInputMetadata entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PricingInputMetadata not found with id: " + id));

        // Update fields from DTO
        entity.setAttributeKey(dto.getAttributeKey());
        entity.setDataType(dto.getDataType());
        entity.setDisplayName(dto.getDisplayName());

        PricingInputMetadata updatedEntity = repository.save(entity);
        reloadService.reloadKieContainer();
        return mapper.toDto(updatedEntity);
    }

    @Transactional
    public void delete(Long id) {
        PricingInputMetadata entity = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("PricingInputMetadata not found with id: " + id));

        if (tierConditionRepository.existsByAttributeName(entity.getAttributeKey())) {
            throw new DependencyViolationException(
                    "Cannot delete PricingInputMetadata '" + entity.getAttributeKey() + "' as it is used in a TierCondition."
            );
        }

        repository.delete(entity);
        reloadService.reloadKieContainer();
    }
}
