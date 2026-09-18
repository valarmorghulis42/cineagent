package com.cineagent.pricing.service;

import com.cineagent.common.error.ErrorCode;
import com.cineagent.common.error.UnprocessableException;
import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.repository.DiscountCodeRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DiscountService {

  private final DiscountCodeRepository discountCodeRepository;
  private final Clock clock;

  public DiscountService(DiscountCodeRepository discountCodeRepository, Clock clock) {
    this.discountCodeRepository = discountCodeRepository;
    this.clock = clock;
  }

  /** Validates without redeeming — used for quote/dry-run and the pre-check before booking. */
  @Transactional(readOnly = true)
  public DiscountCode validate(String code, BigDecimal orderSubtotal) {
    DiscountCode discount =
        discountCodeRepository
            .findByCode(code.toUpperCase())
            .orElseThrow(() -> new UnprocessableException(ErrorCode.DISCOUNT_INVALID, "Unknown discount code"));
    Instant now = Instant.now(clock);
    if (!discount.isCurrentlyValid(now)) {
      throw new UnprocessableException(ErrorCode.DISCOUNT_INVALID, "Discount code is not currently valid");
    }
    if (discount.getUsedCount() >= discount.getMaxRedemptions()) {
      throw new UnprocessableException(ErrorCode.DISCOUNT_EXHAUSTED, "Discount code usage limit reached");
    }
    if (orderSubtotal.compareTo(discount.getMinOrderAmount()) < 0) {
      throw new UnprocessableException(
          ErrorCode.DISCOUNT_MIN_ORDER_NOT_MET,
          "Order must be at least " + discount.getMinOrderAmount() + " to use this code");
    }
    return discount;
  }

  /**
   * Redeems via the conditional-UPDATE cap check. Must be called from within the caller's
   * booking transaction, AFTER seat locks are held (global lock order, AGENTS.md §4.4). Throws if
   * the cap was hit by a concurrent redemption between validate() and this call.
   */
  @Transactional
  public void redeem(Long discountCodeId) {
    int updated = discountCodeRepository.tryRedeem(discountCodeId);
    if (updated == 0) {
      throw new UnprocessableException(ErrorCode.DISCOUNT_EXHAUSTED, "Discount code usage limit reached");
    }
  }
}
