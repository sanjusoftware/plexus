package com.bankengine.pricing.repository;
import com.bankengine.pricing.model.PriceValue;
import org.springframework.data.jpa.repository.JpaRepository;
public interface PriceValueRepository extends JpaRepository<PriceValue, Long> {}