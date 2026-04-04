package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.DependencyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FeatureComponentServiceTest extends BaseServiceTest {

    @Mock private FeatureComponentRepository componentRepository;
    @Mock private ProductFeatureLinkRepository linkRepository;
    @Mock private FeatureComponentMapper featureComponentMapper;

    @InjectMocks
    private FeatureComponentService featureComponentService;

    @Test
    void testSearchFeatures() {
        when(componentRepository.findAll(any(Specification.class))).thenReturn(List.of(new FeatureComponent()));
        featureComponentService.searchFeatures("CODE", 1, VersionableEntity.EntityStatus.ACTIVE);
        verify(componentRepository).findAll(any(Specification.class));
    }

    @Test
    void testGetFeaturesByCode() {
        when(componentRepository.findAllByBankIdAndCode(any(), any())).thenReturn(List.of());
        featureComponentService.getFeaturesByCode("CODE", null);

        when(componentRepository.findAllByBankIdAndCodeAndVersion(any(), any(), any())).thenReturn(List.of());
        featureComponentService.getFeaturesByCode("CODE", 1);
    }

    @Test
    void testUpdateFeature_Collision() {
        FeatureComponent existing = new FeatureComponent();
        existing.setId(1L);
        existing.setBankId(TEST_BANK_ID);
        existing.setCode("OLD");
        existing.setVersion(1);
        existing.setStatus(VersionableEntity.EntityStatus.DRAFT);

        FeatureComponent conflicting = new FeatureComponent();
        conflicting.setId(2L);
        conflicting.setBankId(TEST_BANK_ID);
        conflicting.setCode("NEW");

        when(componentRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(componentRepository.findAllByBankIdAndCode(TEST_BANK_ID, "NEW")).thenReturn(List.of(conflicting));

        FeatureComponentRequest req = new FeatureComponentRequest();
        req.setCode("NEW");

        assertThrows(IllegalStateException.class, () -> featureComponentService.updateFeature(1L, req));
    }

    @Test
    void testDeleteFeature_Linked() {
        FeatureComponent fc = new FeatureComponent();
        fc.setBankId(TEST_BANK_ID);
        when(componentRepository.findById(1L)).thenReturn(Optional.of(fc));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(5L);

        assertThrows(DependencyViolationException.class, () -> featureComponentService.deleteFeature(1L));
    }

    @Test
    void testDeleteFeature_Physical() {
        FeatureComponent fc = new FeatureComponent();
        fc.setBankId(TEST_BANK_ID);
        fc.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(componentRepository.findById(1L)).thenReturn(Optional.of(fc));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(0L);

        featureComponentService.deleteFeature(1L);
        verify(componentRepository).delete(fc);
    }
}
