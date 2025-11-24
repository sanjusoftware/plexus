package com.bankengine.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Table(name = "role")
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", unique = true, nullable = false, length = 50)
    private String name;

    @Column(name = "bank_id", nullable = true)
    private String bankId;

    // Use @ElementCollection to store a set of simple strings (Authorities)
    // The collection value (the authority string) is mapped to the 'authority_name' column.
    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_authority", // Custom join table name
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "authority_name", nullable = false, length = 100)
    private Set<String> authorities;
}