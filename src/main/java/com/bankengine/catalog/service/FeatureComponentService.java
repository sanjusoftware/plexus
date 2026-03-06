package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.dto.VersionRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.service.BaseService;
import com.bankengine.web.exception.DependencyViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FeatureComponentService extends BaseService {

    private final FeatureComponentRepository componentRepository;
    private final ProductFeatureLinkRepository linkRepository;
    private final FeatureComponentMapper featureComponentMapper;

    @Transactional(readOnly = true)
    public List<FeatureComponentResponse> getAllFeatures() {
        return componentRepository.findAll().stream()
                .map(featureComponentMapper::toResponseDto)
                .toList();
    }

    @Transactional(readOnly = true)
    public FeatureComponentResponse getFeatureResponseById(Long id) {
        return featureComponentMapper.toResponseDto(getFeatureComponentById(id));
    }

    public FeatureComponent getFeatureComponentById(Long id) {
        return getByIdSecurely(componentRepository, id, "Feature Component");
    }

    public FeatureComponent getFeatureComponentByCode(String code, Integer version) {
        return getByCodeAndVersionSecurely(componentRepository, code, version, "Feature Component");
    }

    // --- WRITE OPERATIONS ---

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse createFeature(FeatureComponentRequest requestDto) {
        validateNewVersionable(componentRepository, requestDto.getName(), requestDto.getCode());

        FeatureComponent component = featureComponentMapper.toEntity(requestDto);
        component.setBankId(getCurrentBankId());
        component.setStatus(VersionableEntity.EntityStatus.DRAFT);
        component.setVersion(1);

        return featureComponentMapper.toResponseDto(componentRepository.save(component));
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public Long versionFeature(Long oldId, VersionRequest request) {
        FeatureComponent source = getFeatureComponentById(oldId);
        FeatureComponent newVersion = featureComponentMapper.clone(source);

        prepareNewVersion(newVersion, source, request, componentRepository);

        return componentRepository.save(newVersion).getId();
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse updateFeature(Long id, FeatureComponentRequest requestDto) {
        FeatureComponent component = getFeatureComponentById(id);
        validateDraft(component);
        featureComponentMapper.updateFromDto(requestDto, component);
        return featureComponentMapper.toResponseDto(componentRepository.save(component));
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public void activateFeature(Long id) {
        FeatureComponent component = getFeatureComponentById(id);
        validateDraft(component);

        component.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        componentRepository.save(component);
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public void deleteFeature(Long id) {
        FeatureComponent component = getFeatureComponentById(id);
        long linkCount = linkRepository.countByFeatureComponentId(id);
        if (linkCount > 0) {
            throw new DependencyViolationException(
                String.format("Cannot delete feature as it is linked to %d product(s).", linkCount)
            );
        }

        if (component.getStatus() == VersionableEntity.EntityStatus.DRAFT) {
            componentRepository.delete(component);
        } else {
            component.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
            componentRepository.save(component);
        }
    }

}