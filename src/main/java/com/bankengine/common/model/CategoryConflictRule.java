package com.bankengine.common.model;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
public class CategoryConflictRule {
    @Column(name = "category_a", nullable = false, length = 100)
    private String categoryA;

    @Column(name = "category_b", nullable = false, length = 100)
    private String categoryB;

    public CategoryConflictRule(String categoryA, String categoryB) {
        this.categoryA = categoryA;
        this.categoryB = categoryB;
    }

    public boolean isConflict(String cat1, String cat2) {
        return (categoryA.equalsIgnoreCase(cat1) && categoryB.equalsIgnoreCase(cat2)) ||
                (categoryA.equalsIgnoreCase(cat2) && categoryB.equalsIgnoreCase(cat1));
    }
}