package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponse;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.test.config.BaseServiceTest;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class FeatureComponentServiceTest extends BaseServiceTest {

    @Mock
    private FeatureComponentRepository componentRepository;

    @Mock
    private ProductFeatureLinkRepository linkRepository;

    @Mock
    private FeatureComponentMapper featureComponentMapper;

    @InjectMocks
    private FeatureComponentService featureComponentService;

    @Test
    @DisplayName("Should create feature and return response when name is unique")
    void testCreateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");

        when(componentRepository.existsByName(dto.getName())).thenReturn(false);
        when(featureComponentMapper.toEntity(dto)).thenReturn(new FeatureComponent());
        when(componentRepository.save(any(FeatureComponent.class))).thenReturn(new FeatureComponent());
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        FeatureComponentResponse response = featureComponentService.createFeature(dto);

        assertNotNull(response);
        verify(componentRepository, times(1)).save(any(FeatureComponent.class));
    }

    @Test
    @DisplayName("Should throw exception when creating a feature with an existing name")
    void testCreateFeature_withExistingName() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");

        when(componentRepository.existsByName(dto.getName())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> featureComponentService.createFeature(dto));
    }

    @Test
    @DisplayName("Should retrieve internal entity by ID")
    void testGetFeatureComponentById() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));

        FeatureComponent component = featureComponentService.getFeatureComponentById(1L);

        assertNotNull(component);
    }

    @Test
    @DisplayName("Should throw NotFoundException when ID does not exist")
    void testGetFeatureComponentById_notFound() {
        when(componentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> featureComponentService.getFeatureComponentById(1L));
    }

    @Test
    @DisplayName("Should return list of all feature responses")
    void testGetAllFeatures() {
        when(componentRepository.findAll()).thenReturn(Collections.singletonList(new FeatureComponent()));
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        assertEquals(1, featureComponentService.getAllFeatures().size());
    }

    @Test
    @DisplayName("Should update existing feature when requested name is not a conflict")
    void testUpdateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");
        dto.setDataType("STRING");

        FeatureComponent existingComponent = new FeatureComponent();
        existingComponent.setName("Old Feature");

        when(componentRepository.findById(1L)).thenReturn(Optional.of(existingComponent));
        when(componentRepository.existsByName(dto.getName())).thenReturn(false);
        when(componentRepository.save(any(FeatureComponent.class))).thenReturn(new FeatureComponent());
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponse());

        FeatureComponentResponse response = featureComponentService.updateFeature(1L, dto);

        assertNotNull(response);
        verify(componentRepository, times(1)).save(any(FeatureComponent.class));
    }

    @Test
    @DisplayName("Should delete feature when no product links exist")
    void testDeleteFeature() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));
        when(linkRepository.existsByFeatureComponentId(1L)).thenReturn(false);

        featureComponentService.deleteFeature(1L);

        verify(componentRepository, times(1)).delete(any(FeatureComponent.class));
    }

    @Test
    @DisplayName("Should prevent deletion and throw DependencyViolationException when links exist")
    void testDeleteFeature_withDependencies() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));
        when(linkRepository.existsByFeatureComponentId(1L)).thenReturn(true);
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(1L);

        assertThrows(DependencyViolationException.class, () -> featureComponentService.deleteFeature(1L));
    }
}
