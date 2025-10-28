package com.bankengine.catalog.service;

import com.bankengine.catalog.model.FeatureComponent;
import com.bankengine.catalog.repository.FeatureComponentRepository;
import com.bankengine.catalog.repository.ProductFeatureLinkRepository;
import com.bankengine.web.exception.DependencyViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
public class FeatureComponentServiceTest {

    @Mock
    private FeatureComponentRepository featureComponentRepository;

    @Mock
    private ProductFeatureLinkRepository linkRepository;

    @InjectMocks
    private FeatureComponentService featureComponentService;

    @Test
    void shouldThrowDependencyViolationExceptionWhenDeletingLinkedFeature() {
        // Arrange
        Long featureComponentId = 1L;
        FeatureComponent featureComponent = new FeatureComponent();
        featureComponent.setId(featureComponentId);

        when(featureComponentRepository.findById(featureComponentId)).thenReturn(Optional.of(featureComponent));
        when(linkRepository.existsByFeatureComponentId(featureComponentId)).thenReturn(true);
        when(linkRepository.countByFeatureComponentId(featureComponentId)).thenReturn(1L);

        // Act & Assert
        assertThrows(DependencyViolationException.class, () -> {
            featureComponentService.deleteFeature(featureComponentId);
        });
    }
}
