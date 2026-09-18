package com.cineagent.refund.repository;

import com.cineagent.refund.domain.RefundPolicy;
import com.cineagent.refund.domain.RefundScopeType;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefundPolicyRepository extends JpaRepository<RefundPolicy, Long> {

  @Query(
      "select r from RefundPolicy r where r.active = true and r.scopeType = :scopeType "
          + "and ((:scopeId is null and r.scopeId is null) or r.scopeId = :scopeId)")
  List<RefundPolicy> findActiveByScope(
      @Param("scopeType") RefundScopeType scopeType, @Param("scopeId") Long scopeId);
}
