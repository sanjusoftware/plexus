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
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FeatureComponentServiceTest extends BaseServiceTest {

    @Mock
    private FeatureComponentRepository featureComponentRepository;
    @Mock
    private ProductFeatureLinkRepository linkRepository;
    @Mock
    private FeatureComponentMapper featureComponentMapper;

    @InjectMocks
    private FeatureComponentService featureComponentService;

    // --- READ OPERATIONS ---

    @Test
    @DisplayName("Should create feature and return response when name and code are unique")
    void testCreateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");
        dto.setCode("FEAT-001");

        when(featureComponentRepository.existsByNameAndBankId(eq(dto.getName()), any())).thenReturn(false);
        when(featureComponentRepository.existsByBankIdAndCodeAndVersion(any(), eq(dto.getCode()), eq(1))).thenReturn(false);

        FeatureComponent entity = getValidFeatureComponent(VersionableEntity.EntityStatus.DRAFT);
        when(featureComponentMapper.toEntity(dto)).thenReturn(entity);
        when(featureComponentRepository.save(any(FeatureComponent.class))).thenReturn(entity);
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        FeatureComponentResponse response = featureComponentService.createFeature(dto);

        assertNotNull(response);
        verify(featureComponentRepository).save(argThat(f ->
                f.getBankId().equals(TEST_BANK_ID) && f.getVersion() == 1
        ));
    }

    @Test
    @DisplayName("Should throw exception when creating a feature with an existing name")
    void testCreateFeature_withExistingName() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");

        when(featureComponentRepository.existsByNameAndBankId(eq(dto.getName()), any())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> featureComponentService.createFeature(dto));
    }

    @Test
    @DisplayName("Should retrieve internal entity by ID")
    void testGetFeatureComponentById() {
        FeatureComponent entity = getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE);
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(entity));

        FeatureComponent component = featureComponentService.getFeatureComponentById(1L);

        assertNotNull(component);
        assertEquals(TEST_BANK_ID, component.getBankId());
    }

    @Test
    @DisplayName("Should throw NotFoundException when ID does not exist")
    void testGetFeatureComponentById_notFound() {
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> featureComponentService.getFeatureComponentById(1L));
    }

    @Test
    @DisplayName("Should return list of all feature responses")
    void testGetAllFeatures() {
        when(featureComponentRepository.findAll()).thenReturn(Collections.singletonList(getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE)));
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        assertEquals(1, featureComponentService.getAllFeatures().size());
    }

    @Test
    @DisplayName("Should update existing feature when requested name is not a conflict")
    void testUpdateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");

        FeatureComponent existingComponent = getValidFeatureComponent(VersionableEntity.EntityStatus.DRAFT);

        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(existingComponent));
        when(featureComponentRepository.save(any(FeatureComponent.class))).thenReturn(existingComponent);
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        FeatureComponentResponse response = featureComponentService.updateFeature(1L, dto);

        assertNotNull(response);
        verify(featureComponentMapper).updateFromDto(eq(dto), any());
        verify(featureComponentRepository).save(any(FeatureComponent.class));
    }

    @Test
    @DisplayName("Should PHYSICALLY DELETE feature when it is DRAFT and no product links exist")
    void testDeleteFeature_Draft() {
        FeatureComponent draftFeature = getValidFeatureComponent(VersionableEntity.EntityStatus.DRAFT);
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(draftFeature));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(0L);

        featureComponentService.deleteFeature(1L);

        verify(featureComponentRepository, times(1)).delete(draftFeature);
        verify(featureComponentRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should ARCHIVE feature when it is ACTIVE and no product links exist")
    void testDeleteFeature_Active() {
        FeatureComponent activeFeature = getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE);
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(activeFeature));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(0L);

        featureComponentService.deleteFeature(1L);

        assertEquals(VersionableEntity.EntityStatus.ARCHIVED, activeFeature.getStatus());
        verify(featureComponentRepository, never()).delete(any(FeatureComponent.class));
        verify(featureComponentRepository, times(1)).save(activeFeature);
    }

    @Test
    @DisplayName("Should throw DependencyViolationException when links exist")
    void testDeleteFeature_withDependencies() {
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE)));
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(5L);
        DependencyViolationException ex = assertThrows(DependencyViolationException.class,
                () -> featureComponentService.deleteFeature(1L));
        assertEquals("Cannot delete feature as it is linked to 5 product(s).", ex.getMessage());
        verify(featureComponentRepository, never()).delete(any(FeatureComponent.class));
        verify(featureComponentRepository, never()).save(any(FeatureComponent.class));
    }

    @Test
    @DisplayName("Should throw AccessDeniedException if bankId mismatches on retrieval")
    void testGetFeatureComponentById_securityBreach() {
        FeatureComponent entity = getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE);
        entity.setBankId("OTHER_BANK");
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(entity));
        assertThrows(AccessDeniedException.class, () -> featureComponentService.getFeatureComponentById(1L));
    }

    @Test
    @DisplayName("Should create new version and handle collision checks")
    void testVersionFeature_workflow() {
        FeatureComponent source = getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE);
        source.setCode("FEAT-100");
        source.setVersion(1);

        FeatureComponent newVersion = getValidFeatureComponent(VersionableEntity.EntityStatus.DRAFT);
        // Using null for newCode to trigger Version N+1 logic
        VersionRequest request = new VersionRequest("New Name", null, null);

        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(source));
        when(featureComponentMapper.clone(source)).thenReturn(newVersion);
        when(featureComponentRepository.existsByBankIdAndCodeAndVersion(eq(TEST_BANK_ID), eq("FEAT-100"), eq(2)))
                .thenReturn(false);
        when(featureComponentRepository.save(any())).thenReturn(newVersion);

        featureComponentService.versionFeature(1L, request);
        verify(featureComponentRepository).save(argThat(f -> f.getVersion() == 2));
    }

    @Test
    @DisplayName("Should transition status to ACTIVE during activation")
    void testActivateFeature_workflow() {
        FeatureComponent draft = getValidFeatureComponent(VersionableEntity.EntityStatus.DRAFT);
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(draft));

        featureComponentService.activateFeature(1L);

        assertEquals(VersionableEntity.EntityStatus.ACTIVE, draft.getStatus());
        verify(featureComponentRepository).save(draft);
    }

    @Test
    @DisplayName("Should block update if status is not DRAFT")
    void testUpdateFeature_lifecycleGuard() {
        FeatureComponent active = getValidFeatureComponent(VersionableEntity.EntityStatus.ACTIVE);
        when(featureComponentRepository.findById(1L)).thenReturn(Optional.of(active));

        assertThrows(IllegalStateException.class, () -> featureComponentService.updateFeature(1L, new FeatureComponentRequest()));
    }

    private FeatureComponent getValidFeatureComponent(VersionableEntity.EntityStatus status) {
        FeatureComponent component = FeatureComponent.builder()
                .dataType(FeatureComponent.DataType.STRING)
                .name("Feature Name")
                .code("FEAT-CODE")
                .build();
        component.setBankId(TEST_BANK_ID);
        component.setStatus(status);
        component.setVersion(1);
        return component;
    }
}