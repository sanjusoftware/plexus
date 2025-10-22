package com.bankengine.catalog.model;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "feature_component")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FeatureComponent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name; // e.g., "Max_Tenure", "Has_Overdraft"

    @Enumerated(EnumType.STRING)
    private DataType dataType; // To ensure value is stored/read correctly (e.g., STRING, INTEGER, BOOLEAN)

    public enum DataType {
        STRING, INTEGER, BOOLEAN, DECIMAL, DATE
    }
}