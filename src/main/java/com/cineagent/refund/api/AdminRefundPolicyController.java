package com.cineagent.refund.api;

import com.cineagent.refund.api.dto.RefundPolicyDtos.CreateRefundPolicyRequest;
import com.cineagent.refund.api.dto.RefundPolicyDtos.RefundPolicyResponse;
import com.cineagent.refund.domain.RefundPolicy;
import com.cineagent.refund.repository.RefundPolicyRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/refund-policies")
public class AdminRefundPolicyController {

  private final RefundPolicyRepository refundPolicyRepository;

  public AdminRefundPolicyController(RefundPolicyRepository refundPolicyRepository) {
    this.refundPolicyRepository = refundPolicyRepository;
  }

  @PostMapping
  public ResponseEntity<RefundPolicyResponse> create(@Valid @RequestBody CreateRefundPolicyRequest req) {
    RefundPolicy saved =
        refundPolicyRepository.save(
            new RefundPolicy(req.scopeType(), req.scopeId(), req.minMinutesBeforeShow(), req.refundPercentage()));
    return ResponseEntity.status(HttpStatus.CREATED).body(RefundPolicyResponse.from(saved));
  }
}
