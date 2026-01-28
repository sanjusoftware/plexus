package com.bankengine.common.security;

import com.bankengine.common.model.AuditableEntity;
import com.bankengine.web.exception.NotFoundException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class TenantValidatorTest {

    private final TenantValidator validator = new TenantValidator();

    // Anonymous inner class to test AuditableEntity
    static class TestEntity extends AuditableEntity {
        private String bankId;
        @Override public String getBankId() { return bankId; }
        @Override public void setBankId(String bankId) { this.bankId = bankId; }
    }

    @Test
    void validateOwnership_ShouldSucceed_WhenTenantsMatch() {
        TestEntity entity = new TestEntity();
        entity.setBankId("GOLD_BANK");
        assertDoesNotThrow(() -> validator.validateOwnership(entity, 1L, "GOLD_BANK"));
    }

    @Test
    void validateOwnership_ShouldThrowNotFound_WhenTenantMismatch() {
        TestEntity entity = new TestEntity();
        entity.setBankId("OTHER_BANK");
        assertThrows(NotFoundException.class, () ->
                validator.validateOwnership(entity, 1L, "GOLD_BANK")
        );
    }

    @Test
    void validateOwnership_ShouldThrowNotFound_WhenEntityIsNull() {
        assertThrows(NotFoundException.class, () ->
                validator.validateOwnership(null, 999L, "GOLD_BANK")
        );
    }

    @Test
    void validateOwnership_ShouldThrowNotFound_WhenBankIdInContextIsNull() {
        TestEntity entity = new TestEntity();
        entity.setBankId("GOLD_BANK");

        assertThrows(NotFoundException.class, () ->
                validator.validateOwnership(entity, 1L, null)
        );
    }
}