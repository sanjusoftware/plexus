package com.bankengine.catalog;

import com.bankengine.catalog.dto.ProductBundleRequest;
import com.bankengine.catalog.service.ProductBundleService;
import com.bankengine.test.config.AbstractIntegrationTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ProductBundleIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private ProductBundleService bundleService;

    // --- CREATE TESTS ---

    @Test
    @WithMockUser(authorities = "catalog:bundle:create")
    @DisplayName("POST /api/v1/bundles - Success")
    void createProductBundle_ShouldReturnId_WhenValid() throws Exception {
        ProductBundleRequest request = createValidRequest();
        when(bundleService.createBundle(any(ProductBundleRequest.class))).thenReturn(100L);

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(content().string("100"));
    }

    @Test
    @WithMockUser(authorities = "catalog:bundle:create")
    @DisplayName("POST /api/v1/bundles - Validation Failure")
    void createProductBundle_ShouldReturnBadRequest_WhenInvalid() throws Exception {
        ProductBundleRequest invalidRequest = new ProductBundleRequest(); // Empty DTO

        mockMvc.perform(post("/api/v1/bundles")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    // --- UPDATE (VERSIONING) TESTS ---

    @Test
    @WithMockUser(authorities = "catalog:bundle:update")
    @DisplayName("PUT /api/v1/bundles/{id} - Success (Creates New Version)")
    void updateProductBundle_ShouldReturnNewId() throws Exception {
        ProductBundleRequest request = createValidRequest();
        when(bundleService.updateBundle(eq(1L), any(ProductBundleRequest.class))).thenReturn(101L);

        mockMvc.perform(put("/api/v1/bundles/{id}", 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(content().string("101"));
    }

    // --- ACTIVATION TESTS ---

    @Test
    @WithMockUser(authorities = "catalog:bundle:activate")
    @DisplayName("POST /api/v1/bundles/{id}/activate - Success")
    void activateBundle_ShouldReturnOk() throws Exception {
        doNothing().when(bundleService).activateBundle(1L);

        mockMvc.perform(post("/api/v1/bundles/{id}/activate", 1L))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(authorities = "catalog:bundle:read") // Wrong authority
    @DisplayName("POST /api/v1/bundles/{id}/activate - Forbidden")
    void activateBundle_ShouldReturnForbidden_WhenInsufficientPrivileges() throws Exception {
        mockMvc.perform(post("/api/v1/bundles/{id}/activate", 1L))
                .andExpect(status().isForbidden());
    }

    // --- CLONING TESTS ---

    @Test
    @WithMockUser(authorities = "catalog:bundle:create")
    @DisplayName("POST /api/v1/bundles/{id}/clone - Success")
    void cloneBundle_ShouldReturnNewId() throws Exception {
        String newName = "Premium Salary Bundle Copy";
        when(bundleService.cloneBundle(1L, newName)).thenReturn(200L);

        mockMvc.perform(post("/api/v1/bundles/{id}/clone", 1L)
                        .param("newName", newName))
                .andExpect(status().isCreated())
                .andExpect(content().string("200"));
    }

    // --- ARCHIVE TESTS ---

    @Test
    @WithMockUser(authorities = "catalog:bundle:delete")
    @DisplayName("DELETE /api/v1/bundles/{id} - Success")
    void archiveBundle_ShouldReturnNoContent() throws Exception {
        doNothing().when(bundleService).archiveBundle(1L);

        mockMvc.perform(delete("/api/v1/bundles/{id}", 1L))
                .andExpect(status().isNoContent());
    }

    // --- HELPER METHODS ---

    private ProductBundleRequest createValidRequest() {
        ProductBundleRequest request = new ProductBundleRequest();
        request.setCode("BNDL-777");
        request.setName("Gold Savings Bundle");
        request.setEligibilitySegment("RETAIL");
        request.setActivationDate(LocalDate.now().plusDays(1));

        ProductBundleRequest.BundleItemRequest item = new ProductBundleRequest.BundleItemRequest();
        item.setProductId(10L);
        item.setMainAccount(true);
        item.setMandatory(true);

        request.setItems(List.of(item));
        return request;
    }
}