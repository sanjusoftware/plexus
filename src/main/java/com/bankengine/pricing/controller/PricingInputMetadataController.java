package com.bankengine.pricing.controller;

import com.bankengine.pricing.dto.PricingInputMetadataDto;
import com.bankengine.pricing.service.PricingInputMetadataService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "Pricing Input Metadata Management", description = "Manages the attributes used in DRL conditions.")
@RestController
@RequestMapping("/api/v1/pricing-metadata")
@RequiredArgsConstructor
public class PricingInputMetadataController {

    private final PricingInputMetadataService service;

    @GetMapping
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<List<PricingInputMetadataDto>> getAll() {
        return ResponseEntity.ok(service.findAll());
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:metadata:read')")
    public ResponseEntity<PricingInputMetadataDto> getById(@PathVariable Long id) {
        return ResponseEntity.ok(service.findById(id));
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pricing:metadata:create')")
    public ResponseEntity<PricingInputMetadataDto> create(@Valid @RequestBody PricingInputMetadataDto dto) {
        PricingInputMetadataDto createdDto = service.create(dto);
        return new ResponseEntity<>(createdDto, HttpStatus.CREATED);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('pricing:metadata:update')")
    public ResponseEntity<PricingInputMetadataDto> update(@PathVariable Long id, @Valid @RequestBody PricingInputMetadataDto dto) {
        PricingInputMetadataDto updatedDto = service.update(id, dto);
        return ResponseEntity.ok(updatedDto);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('pricing:metadata:delete')")
    public void delete(@PathVariable Long id) {
        service.delete(id);
    }
}
