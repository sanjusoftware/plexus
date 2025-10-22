package com.bankengine.catalog.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Entity
@Table(name = "product")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name; // e.g., "Premier Home Loan"

    @ManyToOne // Many Products belong to one ProductType
    @JoinColumn(name = "product_type_id", nullable = false)
    private ProductType productType;

    private String bankId; // To support multiple banks on the platform
    private LocalDate effectiveDate;
    private String status; // e.g., "ACTIVE", "DRAFT", "INACTIVE"
}