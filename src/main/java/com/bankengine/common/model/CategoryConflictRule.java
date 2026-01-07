package com.bankengine.common.model;

import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Embeddable
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CategoryConflictRule {
    private String categoryA;
    private String categoryB;

    public boolean isConflict(String cat1, String cat2) {
        return (categoryA.equalsIgnoreCase(cat1) && categoryB.equalsIgnoreCase(cat2)) ||
                (categoryA.equalsIgnoreCase(cat2) && categoryB.equalsIgnoreCase(cat1));
    }
}