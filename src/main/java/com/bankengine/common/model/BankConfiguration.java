package com.bankengine.common.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "bank_configuration")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@AttributeOverrides({
        @AttributeOverride(
                name = "bankId",
                column = @Column(name = "bank_id", unique = true)
        )
})
public class BankConfiguration extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "allow_multi_bundle_product", nullable = false)
    private boolean allowProductInMultipleBundles = false;

    // ... other bank-specific configuration flags will go here ...
}