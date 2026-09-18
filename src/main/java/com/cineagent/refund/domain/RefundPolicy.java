package com.cineagent.refund.domain;

import com.cineagent.common.persistence.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.math.BigDecimal;

/**
 * One tier of a refund policy, keyed on minutes-before-show (minutes, not hours — truncating to
 * hours produces off-by-one refunds at every boundary). Rows are immutable once created;
 * "editing" a policy deactivates the old rows and inserts new ones, so a booking's snapshotted
 * {@code refund_policy_id}-derived percentage never changes retroactively.
 */
@Entity
@Table(name = "refund_policy")
public class RefundPolicy extends BaseEntity {

  @Enumerated(EnumType.STRING)
  @Column(name = "scope_type", nullable = false, length = 20)
  private RefundScopeType scopeType;

  @Column(name = "scope_id")
  private Long scopeId;

  @Column(name = "min_minutes_before_show", nullable = false)
  private int minMinutesBeforeShow;

  @Column(name = "refund_percentage", nullable = false, precision = 5, scale = 2)
  private BigDecimal refundPercentage;

  @Column(nullable = false)
  private boolean active = true;

  protected RefundPolicy() {}

  public RefundPolicy(
      RefundScopeType scopeType, Long scopeId, int minMinutesBeforeShow, BigDecimal refundPercentage) {
    this.scopeType = scopeType;
    this.scopeId = scopeId;
    this.minMinutesBeforeShow = minMinutesBeforeShow;
    this.refundPercentage = refundPercentage;
  }

  public RefundScopeType getScopeType() {
    return scopeType;
  }

  public Long getScopeId() {
    return scopeId;
  }

  public int getMinMinutesBeforeShow() {
    return minMinutesBeforeShow;
  }

  public BigDecimal getRefundPercentage() {
    return refundPercentage;
  }
}
