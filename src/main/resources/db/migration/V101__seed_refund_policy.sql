-- Default GLOBAL tiered refund policy, so refund resolution always succeeds even with no
-- city/theater/show-specific override configured. Tiers: >=24h full refund, >=6h half refund,
-- otherwise non-refundable (0% floor row).
INSERT INTO refund_policy (scope_type, scope_id, min_minutes_before_show, refund_percentage, active, created_at, updated_at, created_by, updated_by)
VALUES
    ('GLOBAL', NULL, 1440, 100.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
    ('GLOBAL', NULL, 360, 50.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system'),
    ('GLOBAL', NULL, 0, 0.00, TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');
