package com.bankengine.catalog.model;

import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "product_type", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductType extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Loan", "Credit Card", "CASA"
}