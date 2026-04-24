package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.dto.VersionRequest;
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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FeatureComponentServiceTest extends BaseServiceTest {

    @Mock
    private FeatureComponentRepository repository;
    @Mock
    private ProductFeatureLinkRepository linkRepository;
    @Mock
    private FeatureComponentMapper mapper;

    @InjectMocks
    private FeatureComponentService service;

    @Test
    void testGetAllFeatures() {
        when(repository.findAll()).thenReturn(List.of(new FeatureComponent()));
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());
        assertFalse(service.getAllFeatures().isEmpty());
    }

    @Test
    void testSearchFeatures_Branches() {
        when(repository.findAll(any(Specification.class))).thenReturn(List.of(new FeatureComponent()));
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        // Hit all spec branches
        service.searchFeatures("CODE", 1, VersionableEntity.EntityStatus.ACTIVE);
        service.searchFeatures(null, null, null);

        verify(repository, times(2)).findAll(any(Specification.class));
    }

    @Test
    void testGetFeatureResponseById() {
        FeatureComponent f = new FeatureComponent();
        f.setBankId(TEST_BANK_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(f));
        when(mapper.toResponseDto(f)).thenReturn(new FeatureComponentResponse());
        assertNotNull(service.getFeatureResponseById(1L));
    }

    @Test
    void testGetFeaturesByCode() {
        when(repository.findAllByBankIdAndCode(TEST_BANK_ID, "F1")).thenReturn(List.of(new FeatureComponent()));
        when(repository.findAllByBankIdAndCodeAndVersion(TEST_BANK_ID, "F1", 1)).thenReturn(List.of(new FeatureComponent()));
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        assertFalse(service.getFeaturesByCode("F1", null).isEmpty());
        assertFalse(service.getFeaturesByCode("F1", 1).isEmpty());
    }

    @Test
    void testCreateFeature() {
        FeatureComponentRequest req = new FeatureComponentRequest();
        req.setCode("F1");
        when(repository.existsByBankIdAndCodeAndVersion(any(), eq("F1"), eq(1))).thenReturn(false);
        when(mapper.toEntity(req)).thenReturn(new FeatureComponent());
        when(repository.save(any())).thenReturn(new FeatureComponent());
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        service.createFeature(req);
        verify(repository).save(any());
    }

    @Test
    void testVersionFeature_TemporalBranches() {
        FeatureComponent source = new FeatureComponent();
        source.setId(1L);
        source.setBankId(TEST_BANK_ID);
        source.setCode("F1");
        source.setVersion(1);
        source.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        source.setActivationDate(LocalDate.now());
        source.setExpiryDate(LocalDate.now().plusDays(30));

        FeatureComponent newV = new FeatureComponent();
        newV.setCode("F1");

        when(repository.findById(1L)).thenReturn(Optional.of(source));
        when(mapper.clone(source)).thenReturn(newV);
        when(repository.existsByBankIdAndCodeAndVersion(any(), any(), anyInt())).thenReturn(false);
        when(repository.save(any())).thenReturn(newV);
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        // Inherit dates branch
        service.versionFeature(1L, new VersionRequest());
        assertEquals(source.getActivationDate(), newV.getActivationDate());

        // Explicit dates branch
        LocalDate newAct = LocalDate.now().plusDays(1);
        LocalDate newExp = LocalDate.now().plusDays(10);
        service.versionFeature(1L, new VersionRequest("Name", null, newAct, newExp));
        assertEquals(newAct, newV.getActivationDate());
        assertEquals(newExp, newV.getExpiryDate());

        // Branch check for saved result isActive and source archived
        when(repository.save(newV)).thenAnswer(inv -> {
            FeatureComponent f = inv.getArgument(0);
            f.setStatus(VersionableEntity.EntityStatus.ACTIVE);
            source.setStatus(VersionableEntity.EntityStatus.ARCHIVED);
            return f;
        });
        service.versionFeature(1L, new VersionRequest());
        verify(linkRepository).updateFeatureComponentReference(eq(1L), any());
    }

    @Test
    void testUpdateFeature_UniquenessBranches() {
        FeatureComponent featureComponent = new FeatureComponent();
        featureComponent.setId(1L);
        featureComponent.setBankId(TEST_BANK_ID);
        featureComponent.setCode("OLD");
        featureComponent.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(featureComponent));
        when(repository.save(any())).thenReturn(featureComponent);
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        // Case 1: Same code (skip uniqueness check)
        FeatureComponentRequest req1 = new FeatureComponentRequest();
        req1.setCode("OLD");
        service.updateFeature(1L, req1);
        verify(repository, never()).findAllByBankIdAndCode(any(), eq("OLD"));

        // Case 2: New code, not used
        FeatureComponentRequest req2 = new FeatureComponentRequest();
        req2.setCode("NEW");
        when(repository.findAllByBankIdAndCode(TEST_BANK_ID, "NEW")).thenReturn(List.of());
        service.updateFeature(1L, req2);

        // Case 3: New code, used by another
        FeatureComponent other = new FeatureComponent();
        other.setId(2L);
        when(repository.findAllByBankIdAndCode(TEST_BANK_ID, "DUPE")).thenReturn(List.of(other));
        FeatureComponentRequest req3 = new FeatureComponentRequest();
        req3.setCode("DUPE");
        assertThrows(IllegalStateException.class, () -> service.updateFeature(1L, req3));
    }

    @Test
    void testActivateFeature_Branches() {
        FeatureComponent f = new FeatureComponent();
        f.setId(1L);
        f.setBankId(TEST_BANK_ID);
        f.setStatus(VersionableEntity.EntityStatus.DRAFT);
        when(repository.findById(1L)).thenReturn(Optional.of(f));
        when(repository.save(any())).thenReturn(f);
        when(mapper.toResponseDto(any())).thenReturn(new FeatureComponentResponse());

        // Branch: activationDate provided, current is null
        f.setActivationDate(null);
        LocalDate specificDate = LocalDate.now().plusDays(5);
        service.activateFeature(1L, specificDate);
        assertEquals(specificDate, f.getActivationDate());

        // Branch: activationDate already set
        f.setStatus(VersionableEntity.EntityStatus.DRAFT); // reset status for next call
        f.setActivationDate(LocalDate.now().minusDays(5));
        service.activateFeature(1L, LocalDate.now());
        assertEquals(LocalDate.now().minusDays(5), f.getActivationDate());

        // Single arg version
        f.setStatus(VersionableEntity.EntityStatus.DRAFT);
        service.activateFeature(1L);
    }

    @Test
    void testDeleteFeature_StatusBranches() {
        FeatureComponent f = new FeatureComponent();
        f.setId(1L);
        f.setBankId(TEST_BANK_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(f));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(0L);

        // Draft -> delete
        f.setStatus(VersionableEntity.EntityStatus.DRAFT);
        service.deleteFeature(1L);
        verify(repository).delete(f);

        // Non-draft -> archive
        f.setStatus(VersionableEntity.EntityStatus.ACTIVE);
        when(repository.save(any())).thenReturn(f);
        service.deleteFeature(1L);
        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, f.getStatus());
        verify(repository).save(f);
    }

    @Test
    void testDeleteFeature_HasLinks() {
        FeatureComponent f = new FeatureComponent();
        f.setBankId(TEST_BANK_ID);
        when(repository.findById(1L)).thenReturn(Optional.of(f));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(5L);
        assertThrows(DependencyViolationException.class, () -> service.deleteFeature(1L));
    }
}
