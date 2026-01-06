package com.bankengine.catalog.service;

import com.bankengine.catalog.converter.FeatureComponentMapper;
import com.bankengine.catalog.dto.FeatureComponentRequest;
import com.bankengine.catalog.dto.FeatureComponentResponseDto;
import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.web.exception.DependencyViolationException;
import com.bankengine.web.exception.NotFoundException;
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
public class FeatureComponentServiceTest {

    @Mock
    private FeatureComponentRepository componentRepository;

    @Mock
    private ProductFeatureLinkRepository linkRepository;

    @Mock
    private FeatureComponentMapper featureComponentMapper;

    @InjectMocks
    private FeatureComponentService featureComponentService;

    @Test
    void testCreateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");

        when(componentRepository.existsByName(dto.getName())).thenReturn(false);
        when(featureComponentMapper.toEntity(dto)).thenReturn(new FeatureComponent());
        when(componentRepository.save(any(FeatureComponent.class))).thenReturn(new FeatureComponent());
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponseDto());

        FeatureComponentResponseDto response = featureComponentService.createFeature(dto);

        assertNotNull(response);
        verify(componentRepository, times(1)).save(any(FeatureComponent.class));
    }

    @Test
    void testCreateFeature_withExistingName() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Test Feature");
        dto.setDataType("STRING");

        when(componentRepository.existsByName(dto.getName())).thenReturn(true);

        assertThrows(IllegalArgumentException.class, () -> featureComponentService.createFeature(dto));
    }

    @Test
    void testGetFeatureComponentById() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));

        FeatureComponent component = featureComponentService.getFeatureComponentById(1L);

        assertNotNull(component);
    }

    @Test
    void testGetFeatureComponentById_notFound() {
        when(componentRepository.findById(1L)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> featureComponentService.getFeatureComponentById(1L));
    }

    @Test
    void testGetAllFeatures() {
        when(componentRepository.findAll()).thenReturn(Collections.singletonList(new FeatureComponent()));
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponseDto());

        assertEquals(1, featureComponentService.getAllFeatures().size());
    }

    @Test
    void testUpdateFeature() {
        FeatureComponentRequest dto = new FeatureComponentRequest();
        dto.setName("Updated Feature");
        dto.setDataType("STRING");

        FeatureComponent existingComponent = new FeatureComponent();
        existingComponent.setName("Old Feature");

        when(componentRepository.findById(1L)).thenReturn(Optional.of(existingComponent));
        when(componentRepository.existsByName(dto.getName())).thenReturn(false);
        when(componentRepository.save(any(FeatureComponent.class))).thenReturn(new FeatureComponent());
        when(featureComponentMapper.toResponseDto(any(FeatureComponent.class))).thenReturn(new FeatureComponentResponseDto());

        FeatureComponentResponseDto response = featureComponentService.updateFeature(1L, dto);

        assertNotNull(response);
        verify(componentRepository, times(1)).save(any(FeatureComponent.class));
    }

    @Test
    void testDeleteFeature() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));
        when(linkRepository.existsByFeatureComponentId(1L)).thenReturn(false);

        featureComponentService.deleteFeature(1L);

        verify(componentRepository, times(1)).delete(any(FeatureComponent.class));
    }

    @Test
    void testDeleteFeature_withDependencies() {
        when(componentRepository.findById(1L)).thenReturn(Optional.of(new FeatureComponent()));
        when(linkRepository.existsByFeatureComponentId(1L)).thenReturn(true);
        when(linkRepository.countByFeatureComponentId(1L)).thenReturn(1L);

        assertThrows(DependencyViolationException.class, () -> featureComponentService.deleteFeature(1L));
    }
}
