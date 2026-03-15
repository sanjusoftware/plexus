package com.bankengine.test.config;

import com.bankengine.auth.security.TenantContextHolder;
import com.bankengine.pricing.TestTransactionHelper;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.util.Map;
import java.util.Set;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@MockBean(JwtDecoder.class)
public abstract class AbstractIntegrationTest {
    public static final String TEST_BANK_ID = "BANK_A";
    public static final String TEST_BANK_CLINET_ID = "bank-engine-api";
    public static final String TEST_BANK_ISS_URL = "https://trusted-issuer.com";
    @Autowired protected EntityManager entityManager;

    @BeforeEach
    void setupBankContext() {
        TenantContextHolder.setBankId(TEST_BANK_ID);
    }

    @AfterEach
    void cleanupBankContext() {
        TenantContextHolder.clear();
    }

    protected static void seedBaseRoles(TestTransactionHelper txHelper, Map<String, Set<String>> roleMappings) {
        try {
            TenantContextHolder.setBankId(TEST_BANK_ID);
            roleMappings.forEach(txHelper::getOrCreateRoleInDb);
            txHelper.flushAndClear();
        } finally {
            TenantContextHolder.clear();
        }
    }

    protected MockHttpServletRequestBuilder postWithCsrf(String url, Object... vars) {
        return post(url, vars).with(csrf());
    }

    protected MockHttpServletRequestBuilder putWithCsrf(String url, Object... vars) {
        return put(url, vars).with(csrf());
    }

    protected MockHttpServletRequestBuilder patchWithCsrf(String url, Object... vars) {
        return patch(url, vars).with(csrf());
    }

    protected MockHttpServletRequestBuilder deleteWithCsrf(String url, Object... vars) {
        return delete(url, vars).with(csrf());
    }
}