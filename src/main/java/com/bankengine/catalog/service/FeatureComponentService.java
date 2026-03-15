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
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.web.exception.DependencyViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.jpa.domain.Specification;
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
    public List<FeatureComponentResponse> searchFeatures(String code, Integer version, VersionableEntity.EntityStatus status) {
        Specification<FeatureComponent> spec = Specification.where(null);
        if (code != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("code"), code));
        }
        if (version != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("version"), version));
        }
        if (status != null) {
            spec = spec.and((root, query, cb) -> cb.equal(root.get("status"), status));
        }

        return componentRepository.findAll(spec).stream()
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

    @Transactional(readOnly = true)
    public List<FeatureComponentResponse> getFeaturesByCode(String code, Integer version) {
        List<FeatureComponent> features;
        if (version != null) {
            features = componentRepository.findAllByBankIdAndCodeAndVersion(getCurrentBankId(), code, version);
        } else {
            features = componentRepository.findAllByBankIdAndCode(getCurrentBankId(), code);
        }
        return features.stream()
                .map(featureComponentMapper::toResponseDto)
                .toList();
    }

    // --- WRITE OPERATIONS ---

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse createFeature(FeatureComponentRequest requestDto) {
        sanitizeRequest(requestDto);
        validateNewVersionable(componentRepository, requestDto.getName(), requestDto.getCode());

        FeatureComponent component = featureComponentMapper.toEntity(requestDto);
        component.setBankId(getCurrentBankId());
        component.setStatus(VersionableEntity.EntityStatus.DRAFT);
        component.setVersion(1);

        return featureComponentMapper.toResponseDto(componentRepository.save(component));
    }

    private void sanitizeRequest(FeatureComponentRequest requestDto) {
        requestDto.setCode(CodeGeneratorUtil.sanitizeAsCode(requestDto.getCode()));
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse versionFeature(Long oldId, VersionRequest request) {
        FeatureComponent source = getFeatureComponentById(oldId);
        FeatureComponent newVersion = featureComponentMapper.clone(source);

        prepareNewVersion(newVersion, source, request, componentRepository);

        FeatureComponent saved = componentRepository.save(newVersion);

        // Update links if it was a revision of an ACTIVE component
        if (source.isArchived() && saved.isActive()) {
            linkRepository.updateFeatureComponentReference(source.getId(), saved);
        }

        return featureComponentMapper.toResponseDto(saved);
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse updateFeature(Long id, FeatureComponentRequest requestDto) {
        sanitizeRequest(requestDto);
        FeatureComponent component = getFeatureComponentById(id);
        validateDraft(component);

        // Uniqueness check for code if it's being changed
        if (requestDto.getCode() != null && !requestDto.getCode().equals(component.getCode())) {
            if (componentRepository.existsByBankIdAndCodeAndVersion(getCurrentBankId(), requestDto.getCode(), component.getVersion())) {
                throw new IllegalArgumentException("Entity code '" + requestDto.getCode() + "' version " + component.getVersion() + " already exists.");
            }
        }

        featureComponentMapper.updateFromDto(requestDto, component);
        return featureComponentMapper.toResponseDto(componentRepository.save(component));
    }

    @Transactional
    @CacheEvict(value = {"publicCatalog", "productDetails"}, allEntries = true)
    public FeatureComponentResponse activateFeature(Long id) {
        FeatureComponent component = getFeatureComponentById(id);
        validateDraft(component);

        component.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        return featureComponentMapper.toResponseDto(componentRepository.save(component));
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
