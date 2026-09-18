package com.cineagent.refund.api.dto;

import com.cineagent.refund.domain.RefundPolicy;
import com.cineagent.refund.domain.RefundScopeType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;

public final class RefundPolicyDtos {
  private RefundPolicyDtos() {}

  public record CreateRefundPolicyRequest(
      @NotNull RefundScopeType scopeType,
      Long scopeId,
      @Min(0) int minMinutesBeforeShow,
      @DecimalMin("0") @DecimalMax("100") BigDecimal refundPercentage) {}

  public record RefundPolicyResponse(
      Long id, RefundScopeType scopeType, Long scopeId, int minMinutesBeforeShow, BigDecimal refundPercentage) {
    public static RefundPolicyResponse from(RefundPolicy p) {
      return new RefundPolicyResponse(
          p.getId(), p.getScopeType(), p.getScopeId(), p.getMinMinutesBeforeShow(), p.getRefundPercentage());
    }
  }
}
