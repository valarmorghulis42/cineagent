package com.cineagent.pricing.repository;

import com.cineagent.pricing.domain.DiscountCode;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface DiscountCodeRepository extends JpaRepository<DiscountCode, Long> {

  Optional<DiscountCode> findByCode(String code);

  /**
   * The usage-cap race is a counter race, not a resource race — a single conditional UPDATE is
   * the right tool (AGENTS.md): no lock ordering needed, one round trip, and the row-exclusive
   * lock it takes is held to end-of-transaction, serializing concurrent redemptions. Returns 0
   * rows updated if the cap was already hit, which the caller treats as DISCOUNT_EXHAUSTED. Must
   * run AFTER seat locks in the global lock order.
   */
  @Modifying
  @Query(
      "update DiscountCode d set d.usedCount = d.usedCount + 1 "
          + "where d.id = :id and d.usedCount < d.maxRedemptions")
  int tryRedeem(@Param("id") Long id);
}
