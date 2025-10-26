package com.bankengine.catalog.dto;

import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
public class ProductActivationDto {
    // Used to optionally override the effectiveDate when transitioning from DRAFT to ACTIVE
    private LocalDate effectiveDate;
}