package com.bankengine.catalog;

import com.bankengine.catalog.dto.ProductPricingDto;
import com.bankengine.catalog.dto.ProductPricingSyncDto;
import com.bankengine.catalog.model.Product;
import com.bankengine.catalog.model.ProductType;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.catalog.repository.ProductTypeRepository;
import com.bankengine.pricing.model.PricingComponent;
import com.bankengine.pricing.model.ProductPricingLink;
import com.bankengine.pricing.repository.PricingComponentRepository;
import com.bankengine.pricing.repository.ProductPricingLinkRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(authorities = {"catalog:product:update"})
public class ProductPricingSyncIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ProductRepository productRepository;
    @Autowired private ProductTypeRepository productTypeRepository;
    @Autowired private PricingComponentRepository pricingComponentRepository;
    @Autowired private ProductPricingLinkRepository pricingLinkRepository;

    private Product product;
    private PricingComponent compRate; // CORE_RATE context
    private PricingComponent compFee; // ANNUAL_FEE context
    private PricingComponent compDiscount; // DISCOUNT context

    // Helper method to create a DTO for synchronization
    private ProductPricingDto createPricingDto(PricingComponent component, String context) {
        ProductPricingDto dto = new ProductPricingDto();
        dto.setPricingComponentId(component.getId());
        dto.setContext(context);
        return dto;
    }

    // Helper method to create an initial link entity
    private ProductPricingLink createInitialLink(Product p, PricingComponent pc, String context) {
        ProductPricingLink link = new ProductPricingLink();
        link.setProduct(p);
        link.setPricingComponent(pc);
        link.setContext(context);
        return link;
    }

    @BeforeEach
    void setup() {
        // 1. Setup ProductType
        ProductType type = new ProductType();
        type.setName("Savings Account");
        productTypeRepository.save(type);

        // 2. Setup Product
        product = new Product();
        product.setName("Pricing Sync Product");
        product.setProductType(type);
        productRepository.save(product);

        // 3. Setup Pricing Components
        compRate = new PricingComponent();
        compRate.setName("Standard Interest Rate");
        compRate.setType(PricingComponent.ComponentType.RATE);
        pricingComponentRepository.save(compRate);

        compFee = new PricingComponent();
        compFee.setName("Monthly Maintenance Fee");
        compFee.setType(PricingComponent.ComponentType.FEE);
        pricingComponentRepository.save(compFee);

        compDiscount = new PricingComponent();
        compDiscount.setName("Loyalty Discount");
        compDiscount.setType(PricingComponent.ComponentType.DISCOUNT);
        pricingComponentRepository.save(compDiscount);
    }

    // =================================================================
    // 0. SECURITY TEST
    // =================================================================

    @Test
    @WithMockUser(authorities = {"some:other:permission"}) // Override default authority
    void shouldReturn403WhenSyncingPricingWithoutPermission() throws Exception {
        ProductPricingSyncDto syncDto = new ProductPricingSyncDto();
        syncDto.setPricingComponents(List.of(createPricingDto(compRate, "RATE")));

        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isForbidden());
    }

    // =================================================================
    // 1. INITIAL CREATE (No existing links)
    // =================================================================

    @Test
    void shouldCreateNewPricingLinksWhenNoneExist() throws Exception {
        // ARRANGE: Target state includes compRate and compFee
        ProductPricingSyncDto syncDto = new ProductPricingSyncDto();
        syncDto.setPricingComponents(List.of(
                createPricingDto(compRate, "CORE_RATE"),
                createPricingDto(compFee, "MONTHLY_FEE")
        ));

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk());

        // VERIFY: Check DB count
        assertThat(pricingLinkRepository.findByProductId(product.getId())).hasSize(2);
    }

    // =================================================================
    // 2. CREATE and DELETE (Full Sync)
    // =================================================================

    @Test
    void shouldPerformFullSync_CreateAndDelete() throws Exception {
        // ARRANGE: Setup initial state (Only compRate and compDiscount linked)
        pricingLinkRepository.save(createInitialLink(product, compRate, "CORE_RATE")); // Initial Rate
        pricingLinkRepository.save(createInitialLink(product, compDiscount, "LOYALTY_DISCOUNT")); // Initial Discount

        // ARRANGE: Target state (compFee is created, compDiscount is deleted)
        ProductPricingSyncDto syncDto = new ProductPricingSyncDto();
        syncDto.setPricingComponents(List.of(
                createPricingDto(compRate, "CORE_RATE"), // Remains unchanged
                createPricingDto(compFee, "ANNUAL_FEE") // New fee link created
        ));
        // compDiscount link is missing, so it should be deleted.

        // ACT: Synchronize
        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isOk());

        // VERIFY 1: Check DB final state (Only Rate and Fee exist)
        List<ProductPricingLink> finalLinks = pricingLinkRepository.findByProductId(product.getId());
        assertThat(finalLinks).hasSize(2);

        // VERIFY 2: Check composition (should contain CORE_RATE and ANNUAL_FEE contexts)
        List<String> finalContexts = finalLinks.stream()
                .map(ProductPricingLink::getContext)
                .collect(Collectors.toList());
        assertThat(finalContexts).containsExactlyInAnyOrder("CORE_RATE", "ANNUAL_FEE");

        // VERIFY 3: Check deletion (Discount link should be gone)
        assertThat(pricingLinkRepository.existsByPricingComponentId(compDiscount.getId())).isFalse();
    }

    // =================================================================
    // 3. ERROR HANDLING (Not Found)
    // =================================================================

    @Test
    void shouldReturn404OnSyncWithNonExistentProductOrComponent() throws Exception {
        ProductPricingSyncDto syncDto = new ProductPricingSyncDto();
        // A DTO linking to a valid component
        syncDto.setPricingComponents(List.of(createPricingDto(compRate, "RATE")));

        // Test 1: Non-existent Product ID
        mockMvc.perform(put("/api/v1/products/99999/pricing")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(syncDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Product not found")));

        // Test 2: Non-existent Pricing Component ID (must be mocked to hit service logic)
        ProductPricingSyncDto badSyncDto = new ProductPricingSyncDto();
        badSyncDto.setPricingComponents(List.of(
                new ProductPricingDto() {{
                    setPricingComponentId(99999L);
                    setContext("BAD_LINK");
                }}
        ));

        mockMvc.perform(put("/api/v1/products/{id}/pricing", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(badSyncDto)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message", containsString("Pricing Component not found")));
    }
}