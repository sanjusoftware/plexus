package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.AuditableEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "feature_component", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "name"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@RequiredArgsConstructor
@TenantEntity
public class FeatureComponent extends AuditableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    @NonNull
    private String name; // e.g., "Max_Tenure", "Has_Overdraft"

    @Enumerated(EnumType.STRING)
    @NonNull
    private DataType dataType; // To ensure value is stored/read correctly (e.g., STRING, INTEGER, BOOLEAN)

    public enum DataType {
        STRING, INTEGER, BOOLEAN, DECIMAL, DATE
    }
}