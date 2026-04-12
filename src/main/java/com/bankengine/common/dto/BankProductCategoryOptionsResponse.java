package com.bankengine.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BankProductCategoryOptionsResponse {
    private List<String> categories;
    private List<String> examples;
}

