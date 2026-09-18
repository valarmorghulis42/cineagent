package com.cineagent.refund.service;

import com.cineagent.refund.domain.RefundPolicy;
import com.cineagent.refund.domain.RefundScopeType;
import com.cineagent.refund.repository.RefundPolicyRepository;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Most-specific-scope-wins resolution: SHOW &gt; THEATER &gt; CITY &gt; GLOBAL. Deliberately
 * unlike pricing rules (which compose/stack) — a refund policy is a single promise, not a set of
 * additive facts, so only one scope's tiers ever apply.
 */
@Service
public class RefundPolicyResolver {

  private final RefundPolicyRepository refundPolicyRepository;

  public RefundPolicyResolver(RefundPolicyRepository refundPolicyRepository) {
    this.refundPolicyRepository = refundPolicyRepository;
  }

  @Transactional(readOnly = true)
  public BigDecimal resolvePercentage(Long showId, Long theaterId, Long cityId, long minutesBeforeShow) {
    for (ScopeCandidate candidate :
        List.of(
            new ScopeCandidate(RefundScopeType.SHOW, showId),
            new ScopeCandidate(RefundScopeType.THEATER, theaterId),
            new ScopeCandidate(RefundScopeType.CITY, cityId),
            new ScopeCandidate(RefundScopeType.GLOBAL, null))) {
      List<RefundPolicy> tiers = refundPolicyRepository.findActiveByScope(candidate.type(), candidate.id());
      if (!tiers.isEmpty()) {
        return tiers.stream()
            .filter(t -> minutesBeforeShow >= t.getMinMinutesBeforeShow())
            .max(Comparator.comparingInt(RefundPolicy::getMinMinutesBeforeShow))
            .map(RefundPolicy::getRefundPercentage)
            .orElse(BigDecimal.ZERO);
      }
    }
    // Unreachable once the GLOBAL seed policy exists — fail loud rather than silently 0-refund.
    throw new IllegalStateException("No refund policy configured at any scope, including GLOBAL");
  }

  private record ScopeCandidate(RefundScopeType type, Long id) {}
}
