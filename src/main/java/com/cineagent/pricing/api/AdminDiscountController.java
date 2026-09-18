package com.cineagent.pricing.api;

import com.cineagent.pricing.api.dto.DiscountDtos.CreateDiscountRequest;
import com.cineagent.pricing.api.dto.DiscountDtos.DiscountResponse;
import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.repository.DiscountCodeRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/discounts")
public class AdminDiscountController {

  private final DiscountCodeRepository discountCodeRepository;

  public AdminDiscountController(DiscountCodeRepository discountCodeRepository) {
    this.discountCodeRepository = discountCodeRepository;
  }

  @PostMapping
  public ResponseEntity<DiscountResponse> create(@Valid @RequestBody CreateDiscountRequest req) {
    DiscountCode saved =
        discountCodeRepository.save(
            new DiscountCode(
                req.code(),
                req.discountType(),
                req.value(),
                req.minOrderAmount(),
                req.maxDiscountAmount(),
                req.maxRedemptions(),
                req.validFrom(),
                req.validUntil()));
    return ResponseEntity.status(HttpStatus.CREATED).body(DiscountResponse.from(saved));
  }
}
