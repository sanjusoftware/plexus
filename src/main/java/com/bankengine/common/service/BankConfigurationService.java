package com.bankengine.common.service;

import com.bankengine.auth.model.Role;
import com.bankengine.auth.repository.RoleRepository;
import com.bankengine.auth.service.AuthorityDiscoveryService;
import com.bankengine.auth.service.PermissionMappingService;
import com.bankengine.catalog.model.BundleProductLink;
import com.bankengine.catalog.model.ProductCategory;
import com.bankengine.catalog.repository.BundleProductLinkRepository;
import com.bankengine.catalog.repository.ProductCategoryRepository;
import com.bankengine.catalog.repository.ProductRepository;
import com.bankengine.common.annotation.SystemAdminBypass;
import com.bankengine.common.dto.BankConfigurationRequest;
import com.bankengine.common.dto.BankConfigurationResponse;
import com.bankengine.common.dto.BankProductCategoryOptionsResponse;
import com.bankengine.common.model.BankConfiguration;
import com.bankengine.common.model.BankStatus;
import com.bankengine.common.model.CategoryConflictRule;
import com.bankengine.common.model.VersionableEntity;
import com.bankengine.common.repository.BankConfigurationRepository;
import com.bankengine.common.util.CodeGeneratorUtil;
import com.bankengine.data.seeding.CoreMetadataSeeder;
import com.bankengine.web.exception.NotFoundException;
import com.bankengine.web.exception.ValidationException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BankConfigurationService extends BaseService {

    private final BankConfigurationRepository bankConfigurationRepository;
    private final ProductRepository productRepository;
    private final ProductCategoryRepository productCategoryRepository;
    private final BundleProductLinkRepository bundleProductLinkRepository;
    private final RoleRepository roleRepository;
    private final AuthorityDiscoveryService authorityDiscoveryService;
    private final PermissionMappingService permissionMappingService;
    private final CoreMetadataSeeder coreMetadataSeeder;

    @Value("${springdoc.swagger-ui.oauth.client-id:}")
    private String defaultClientId;

    private static final List<String> DEFAULT_CATEGORY_EXAMPLES = List.of(
            "RETAIL",
            "WEALTH",
            "CORPORATE",
            "INVESTMENT",
            "ISLAMIC",
            "SME"
    );

    private String getSystemClientId() {
        return bankConfigurationRepository.findByBankIdUnfiltered(getSystemBankId())
                .map(BankConfiguration::getClientId)
                .orElse(defaultClientId);
    }

    @Transactional
    @SystemAdminBypass // Allows SYSTEM to create/update across tenants
    public BankConfigurationResponse createBank(BankConfigurationRequest request) {
        validateRequest(request);
        String bankId = deriveBankId(request);

        if (bankConfigurationRepository.findByBankIdUnfiltered(bankId).isPresent()) {
             throw new IllegalStateException("Bank already exists: " + bankId);
        }

        BankConfiguration config = new BankConfiguration();
        config.setBankId(bankId);
        config.setName(request.getName());
        config.setIssuerUrl(request.getIssuerUrl() != null ? request.getIssuerUrl().replaceAll("/$", "") : null);
        config.setClientId(request.getClientId() != null && !request.getClientId().isBlank()
                ? request.getClientId() : getSystemClientId());
        config.setClientSecret(request.getClientSecret());
        config.setStatus(BankStatus.DRAFT);
        config.setAdminName(request.getAdminName());
        config.setAdminEmail(request.getAdminEmail());

        if (request.getCurrencyCode() != null) {
            config.setCurrencyCode(request.getCurrencyCode());
        }
        if (request.getAllowProductInMultipleBundles() != null) {
            config.setAllowProductInMultipleBundles(request.getAllowProductInMultipleBundles());
        }

        if (request.getCategoryConflictRules() != null) {
            config.setCategoryConflictRules(toConflictRules(bankId, request.getCategoryConflictRules()));
        }

        bankConfigurationRepository.save(config);
        coreMetadataSeeder.seedCorePricingInputMetadata(bankId);

        return mapToResponse(config);
    }

    private String deriveBankId(BankConfigurationRequest request) {
        if (request.getBankId() != null && !request.getBankId().isBlank()) {
            return CodeGeneratorUtil.sanitizeAsCode(request.getBankId());
        }
        if (request.getName() != null && !request.getName().isBlank()) {
            return CodeGeneratorUtil.sanitizeAsCode(request.getName());
        }
        throw new IllegalArgumentException("Bank ID or Name is required to derive Bank ID.");
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse submitOnboarding(BankConfigurationRequest request) {
        validateOnboardingRequest(request);
        validateRequest(request);
        String bankId = deriveBankId(request);

        if (bankConfigurationRepository.findByBankIdUnfiltered(bankId).isPresent()) {
            throw new IllegalStateException("Bank ID already in use: " + bankId);
        }

        BankConfiguration config = BankConfiguration.builder()
                .bankId(bankId)
                .name(request.getName())
                .issuerUrl(request.getIssuerUrl() != null ? request.getIssuerUrl().replaceAll("/$", "") : null)
                .clientId(request.getClientId().trim())
                .currencyCode(request.getCurrencyCode())
                .adminName(request.getAdminName())
                .adminEmail(request.getAdminEmail())
                .status(BankStatus.DRAFT)
                .allowProductInMultipleBundles(true)
                .categoryConflictRules(toConflictRules(bankId, request.getCategoryConflictRules()))
                .build();

        bankConfigurationRepository.save(config);
        coreMetadataSeeder.seedCorePricingInputMetadata(bankId);
        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse updateBank(BankConfigurationRequest request, boolean isSystemAdmin) {
        return updateBankInternal(getCurrentBankId(), request, isSystemAdmin);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse updateBankById(String bankId, BankConfigurationRequest request) {
        return updateBankInternal(bankId, request, true);
    }

    private BankConfigurationResponse updateBankInternal(String bankId, BankConfigurationRequest request, boolean isSystemAdmin) {
        validateRequest(request);
        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        // System admin can update any bank in any status
        // Bank admin can only update approved (ACTIVE) banks, and only specific fields
        if (!isSystemAdmin && config.getStatus() != BankStatus.ACTIVE) {
            throw new IllegalStateException("Bank Admin can only edit banks that are ACTIVE (approved by System Admin).");
        }

        // If not system admin, restrict what can be updated
        if (!isSystemAdmin) {
            updateBankAdminFields(config, request);
        } else {
            // System admin can update all fields
            updateAllBankFields(config, request);
        }

        bankConfigurationRepository.save(config);
        coreMetadataSeeder.seedCorePricingInputMetadata(config.getBankId());
        return mapToResponse(config);
    }

    private void updateBankAdminFields(BankConfiguration config, BankConfigurationRequest request) {
        // Bank admin can only update: allowProductInMultipleBundles and categoryConflictRules
        if (request.getAllowProductInMultipleBundles() != null) {
            config.setAllowProductInMultipleBundles(request.getAllowProductInMultipleBundles());
        }

        if (request.getCategoryConflictRules() != null) {
            validateConflictRulesAgainstExistingBundles(config.getBankId(), request.getCategoryConflictRules());
            replaceCategoryConflictRules(config, request.getCategoryConflictRules());
        }

    }

    private void validateOnboardingRequest(BankConfigurationRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required.");
        }

        if ((request.getBankId() == null || request.getBankId().isBlank())
                && (request.getName() == null || request.getName().isBlank())) {
            throw new ValidationException("Bank ID or Name is required.");
        }
        if (request.getIssuerUrl() == null || request.getIssuerUrl().isBlank()) {
            throw new ValidationException("Issuer URL is required for onboarding.");
        }
        if (request.getClientId() == null || request.getClientId().isBlank()) {
            throw new ValidationException("Client ID is required for onboarding.");
        }
        if (request.getCurrencyCode() == null || request.getCurrencyCode().isBlank()) {
            throw new ValidationException("Currency code is required for onboarding.");
        }
        if (request.getAdminName() == null || request.getAdminName().isBlank()) {
            throw new ValidationException("Admin name is required for onboarding.");
        }
        if (request.getAdminEmail() == null || request.getAdminEmail().isBlank()) {
            throw new ValidationException("Admin email is required for onboarding.");
        }
    }

    private void validateRequest(BankConfigurationRequest request) {
        if (request == null) {
            throw new ValidationException("Request body is required.");
        }

        if (request.getCategoryConflictRules() != null) {
            java.util.Set<String> seenPairs = new HashSet<>();
            for (BankConfigurationRequest.CategoryConflictDto rule : request.getCategoryConflictRules()) {
                String catA = rule.getCategoryA() != null ? rule.getCategoryA().trim().toUpperCase() : "";
                String catB = rule.getCategoryB() != null ? rule.getCategoryB().trim().toUpperCase() : "";

                if (catA.isEmpty() || catB.isEmpty()) {
                    throw new ValidationException("Both categories in a conflict rule must be specified.");
                }

                if (catA.equals(catB)) {
                    throw new ValidationException("A category cannot conflict with itself: " + catA);
                }

                String pair = catA.compareTo(catB) < 0 ? catA + "|" + catB : catB + "|" + catA;
                if (seenPairs.contains(pair)) {
                    throw new ValidationException("Duplicate conflict rule: " + catA + " and " + catB);
                }
                seenPairs.add(pair);
            }
        }
    }

    private void updateAllBankFields(BankConfiguration config, BankConfigurationRequest request) {
        // System admin can update all fields (DRAFT or ACTIVE banks)
        if (request.getName() != null) {
            config.setName(request.getName());
        }

        if (request.getIssuerUrl() != null) {
            config.setIssuerUrl(request.getIssuerUrl().replaceAll("/$", ""));
        }

        if (request.getClientId() != null) {
            config.setClientId(request.getClientId().isBlank() ? getSystemClientId() : request.getClientId());
        }

        if (request.getClientSecret() != null && !request.getClientSecret().isBlank()) {
            config.setClientSecret(request.getClientSecret());
        }

        if (request.getCurrencyCode() != null) {
            config.setCurrencyCode(request.getCurrencyCode());
        }

        if (request.getAllowProductInMultipleBundles() != null) {
            config.setAllowProductInMultipleBundles(request.getAllowProductInMultipleBundles());
        }

        if (request.getAdminName() != null) {
            config.setAdminName(request.getAdminName());
        }

        if (request.getAdminEmail() != null) {
            config.setAdminEmail(request.getAdminEmail());
        }

        if (request.getCategoryConflictRules() != null) {
            validateConflictRulesAgainstExistingBundles(config.getBankId(), request.getCategoryConflictRules());
            replaceCategoryConflictRules(config, request.getCategoryConflictRules());
        }
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse activateBank(String bankId) {
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));
        if (config.getStatus() == BankStatus.REJECTED) {
            throw new IllegalStateException("Rejected banks cannot be activated.");
        }
        if (config.getStatus() != BankStatus.DRAFT && config.getStatus() != BankStatus.INACTIVE) {
            throw new IllegalStateException("Only DRAFT or INACTIVE banks can be activated.");
        }

        config.setStatus(BankStatus.ACTIVE);
        bankConfigurationRepository.save(config);
        coreMetadataSeeder.seedCorePricingInputMetadata(bankId);

        if (roleRepository.findByNameAndBankId("BANK_ADMIN", bankId).isEmpty()) {
            createBankAdminRole(bankId);
        }

        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse rejectBank(String bankId) {
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (config.getStatus() != BankStatus.DRAFT) {
            throw new IllegalStateException("Only banks in DRAFT status can be rejected.");
        }

        config.setStatus(BankStatus.REJECTED);
        bankConfigurationRepository.save(config);

        roleRepository.findByNameAndBankId("BANK_ADMIN", bankId).ifPresent(roleRepository::delete);

        return mapToResponse(config);
    }

    @Transactional
    @SystemAdminBypass
    public BankConfigurationResponse deactivateBank(String bankId) {
        if (bankId.equals(getCurrentBankId())) {
            throw new IllegalStateException("System Admin cannot deactivate their own bank.");
        }
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        if (config.getStatus() != BankStatus.ACTIVE) {
            throw new IllegalStateException("Only ACTIVE banks can be deactivated.");
        }

        config.setStatus(BankStatus.INACTIVE);
        bankConfigurationRepository.save(config);
        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public java.util.List<BankConfigurationResponse> getAllBanks() {
        String currentBankId = getCurrentBankId();
        if (!getSystemBankId().equals(currentBankId)) {
            throw new org.springframework.security.access.AccessDeniedException("System Admin authority required.");
        }
        return bankConfigurationRepository.findAll().stream()
                .filter(b -> !b.getBankId().equals(currentBankId))
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankConfigurationResponse getBank(String bankId) {
        validateTenantAccess(bankId);
        BankConfiguration config = bankConfigurationRepository.findByBankId(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        return mapToResponse(config);
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankConfigurationResponse getPublicBankConfig(String bankId) {
        // Unfiltered because the user is not yet authenticated, so no tenant context
        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        return BankConfigurationResponse.builder()
                .bankId(config.getBankId())
                .issuerUrl(config.getIssuerUrl())
                .clientId(config.getClientId())
                .hasClientSecret(config.getClientSecret() != null && !config.getClientSecret().isBlank())
                .build();
    }

    @Transactional(readOnly = true)
    @SystemAdminBypass
    public BankProductCategoryOptionsResponse getProductCategoryOptions(String bankId) {
        validateTenantAccess(bankId);

        BankConfiguration config = bankConfigurationRepository.findByBankIdUnfiltered(bankId)
                .orElseThrow(() -> new NotFoundException("Bank not found: " + bankId));

        Set<String> categories = new TreeSet<>();


        categories.addAll(productCategoryRepository.findCategoryCodesByBankId(bankId).stream()
                .map(this::normalizeCategory)
                .filter(value -> !value.isBlank())
                .toList());

        categories.addAll(productRepository.findDistinctCategoriesByBankId(bankId).stream()
                .map(this::normalizeCategory)
                .filter(value -> !value.isBlank())
                .toList());

        if (config.getCategoryConflictRules() != null) {
            for (CategoryConflictRule rule : config.getCategoryConflictRules()) {
                if (rule.getCategoryA() != null && !rule.getCategoryA().isBlank()) {
                    categories.add(normalizeCategory(rule.getCategoryA()));
                }
                if (rule.getCategoryB() != null && !rule.getCategoryB().isBlank()) {
                    categories.add(normalizeCategory(rule.getCategoryB()));
                }
            }
        }

        return BankProductCategoryOptionsResponse.builder()
                .categories(categories.stream().toList())
                .examples(DEFAULT_CATEGORY_EXAMPLES)
                .build();
    }

    private String normalizeCategory(String value) {
        return value == null ? "" : value.trim().toUpperCase();
    }

    private void validateConflictRulesAgainstExistingBundles(String bankId,
                                                             List<BankConfigurationRequest.CategoryConflictDto> conflictRules) {
        if (conflictRules == null || conflictRules.isEmpty()) {
            return;
        }

        Set<String> conflictPairs = new HashSet<>();
        for (BankConfigurationRequest.CategoryConflictDto rule : conflictRules) {
            String catA = normalizeCategory(rule.getCategoryA());
            String catB = normalizeCategory(rule.getCategoryB());
            if (catA.isBlank() || catB.isBlank() || catA.equals(catB)) {
                continue;
            }
            conflictPairs.add(toPairKey(catA, catB));
        }

        if (conflictPairs.isEmpty()) {
            return;
        }

        Set<VersionableEntity.EntityStatus> bundleStatuses = Set.of(
                VersionableEntity.EntityStatus.DRAFT,
                VersionableEntity.EntityStatus.ACTIVE
        );
        List<BundleProductLink> links = bundleProductLinkRepository.findAllByBankIdAndBundleStatuses(bankId, bundleStatuses);

        Map<Long, Set<String>> bundleCategories = new HashMap<>();
        Map<Long, String> bundleCodeById = new HashMap<>();

        for (BundleProductLink link : links) {
            if (link.getProduct() == null || link.getProductBundle() == null) {
                continue;
            }
            String category = normalizeCategory(link.getProduct().getCategory());
            if (category.isBlank()) {
                continue;
            }
            Long bundleId = link.getProductBundle().getId();
            bundleCategories.computeIfAbsent(bundleId, ignored -> new HashSet<>()).add(category);
            bundleCodeById.putIfAbsent(bundleId, link.getProductBundle().getCode());
        }

        for (Map.Entry<Long, Set<String>> entry : bundleCategories.entrySet()) {
            List<String> categories = entry.getValue().stream().sorted().toList();
            for (int i = 0; i < categories.size(); i++) {
                for (int j = i + 1; j < categories.size(); j++) {
                    String pair = toPairKey(categories.get(i), categories.get(j));
                    if (conflictPairs.contains(pair)) {
                        String bundleCode = bundleCodeById.getOrDefault(entry.getKey(), "UNKNOWN");
                        throw new ValidationException("Conflict rule " + pair.replace("|", " <-> ")
                                + " cannot be added because existing bundle '" + bundleCode + "' already contains both categories.");
                    }
                }
            }
        }
    }

    private String toPairKey(String a, String b) {
        return a.compareTo(b) < 0 ? a + "|" + b : b + "|" + a;
    }

    private void validateTenantAccess(String requestedBankId) {
        String currentBankId = getCurrentBankId();
        // If not SYSTEM and not the owner, it's a 404
        if (!getSystemBankId().equals(currentBankId) && !currentBankId.equals(requestedBankId)) {
            log.warn("[SECURITY] Tenant mismatch! User {} tried to access {}", currentBankId, requestedBankId);
            throw new NotFoundException("Bank configuration not found for: " + requestedBankId);
        }
    }

    private void createBankAdminRole(String bankId) {
        Set<String> allAuthorities = authorityDiscoveryService.discoverAllAuthorities();

        // Filter out system-level authorities for bank-level super admins to ensure proper isolation
        Set<String> bankAuthorities = allAuthorities.stream()
                .filter(auth -> !auth.startsWith("system:"))
                .collect(Collectors.toSet());

        // Add bank-specific configuration permissions
        bankAuthorities.add("bank:config:read");
        bankAuthorities.add("bank:config:write");
        bankAuthorities.add("bank:stats:read");

        Role superAdmin = new Role();
        superAdmin.setName("BANK_ADMIN");
        superAdmin.setBankId(bankId);
        superAdmin.setAuthorities(new HashSet<>(bankAuthorities));
        roleRepository.save(superAdmin);

        // Evict cache to ensure new role is recognized
        permissionMappingService.evictAllRolePermissionsCache();
    }

    private BankConfigurationResponse mapToResponse(BankConfiguration config) {
        List<CategoryConflictRule> conflictRules = config.getCategoryConflictRules() == null
                ? List.of()
                : config.getCategoryConflictRules();

        return BankConfigurationResponse.builder()
                .bankId(config.getBankId())
                .name(config.getName())
                .allowProductInMultipleBundles(config.isAllowProductInMultipleBundles())
                .issuerUrl(config.getIssuerUrl())
                .clientId(config.getClientId())
                .hasClientSecret(config.getClientSecret() != null && !config.getClientSecret().isBlank())
                .categoryConflictRules(conflictRules.stream()
                        .map(r -> new BankConfigurationRequest.CategoryConflictDto(r.getCategoryA(), r.getCategoryB()))
                        .collect(Collectors.toList()))
                .currencyCode(config.getCurrencyCode())
                .status(config.getStatus().name())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .adminName(config.getAdminName())
                .adminEmail(config.getAdminEmail())
                .build();
    }

    private List<CategoryConflictRule> toConflictRules(String bankId,
                                                       List<BankConfigurationRequest.CategoryConflictDto> dtoRules) {
        if (dtoRules == null || dtoRules.isEmpty()) {
            return new ArrayList<>();
        }

        return dtoRules.stream()
                .map(dto -> {
                    String categoryA = normalizeCategory(dto.getCategoryA());
                    String categoryB = normalizeCategory(dto.getCategoryB());

                    // Auto-upsert keeps conflict categories aligned with the tenant master list.
                    ensureCategoryExists(bankId, categoryA);
                    ensureCategoryExists(bankId, categoryB);

                    return new CategoryConflictRule(categoryA, categoryB);
                })
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private void ensureCategoryExists(String bankId, String normalizedCode) {
        if (normalizedCode == null || normalizedCode.isBlank()) {
            throw new ValidationException("Category code is required.");
        }

        productCategoryRepository.findByBankIdAndCode(bankId, normalizedCode)
                .orElseGet(() -> productCategoryRepository.save(ProductCategory.builder()
                        .bankId(bankId)
                        .code(normalizedCode)
                        .name(normalizedCode)
                        .build()));
    }

    private void replaceCategoryConflictRules(BankConfiguration config,
                                              List<BankConfigurationRequest.CategoryConflictDto> dtoRules) {
        config.setCategoryConflictRules(toConflictRules(config.getBankId(), dtoRules));
    }
}
