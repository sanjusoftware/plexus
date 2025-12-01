package com.bankengine.auth.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

@Entity
@Table(name = "role", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"bank_id", "name"})
})
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Role {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "name", nullable = false, length = 50)
    private String name;

    @Column(name = "bank_id", nullable = true)
    private String bankId;

    @ElementCollection(fetch = FetchType.EAGER)
    @CollectionTable(
            name = "role_authority",
            joinColumns = @JoinColumn(name = "role_id")
    )
    @Column(name = "authority_name", nullable = false, length = 100)
    private Set<String> authorities;
}