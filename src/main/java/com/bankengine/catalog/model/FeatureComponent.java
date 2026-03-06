package com.bankengine.catalog.model;

import com.bankengine.common.annotation.TenantEntity;
import com.bankengine.common.model.VersionableEntity;
import jakarta.persistence.*;
import lombok.*;
import lombok.experimental.SuperBuilder;

@Entity
@Table(name = "feature_component", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "code", "version"})
})
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
@TenantEntity
public class FeatureComponent extends VersionableEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;


    @Enumerated(EnumType.STRING)
    @NonNull
    private DataType dataType;

    public enum DataType {
        STRING, INTEGER, BOOLEAN, DECIMAL, DATE
    }
}