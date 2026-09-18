-- Seed data that must exist on every environment (not demo-only): one admin account so a
-- reviewer can authenticate against admin-only endpoints without a bootstrap dance, and one
-- sample customer. Credentials are documented in README's quickstart.
--
-- Passwords: admin@cineagent.dev / Admin@123 ; customer@cineagent.dev / Customer@123
-- (BCrypt hashes generated once via BCryptPasswordEncoder — dev-only credentials, not secrets.)

INSERT INTO app_user (email, password_hash, full_name, phone, enabled, created_at, updated_at, created_by, updated_by)
VALUES ('admin@cineagent.dev', '$2a$10$cheLzXDGMy9/DAxi.J8YP.ZrJq5TTSa3yMIjIGAh.pRuqAUSxRNPu', 'CineAgent Admin', '+910000000001', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

INSERT INTO user_role (user_id, role)
SELECT id, 'ADMIN' FROM app_user WHERE email = 'admin@cineagent.dev';

INSERT INTO app_user (email, password_hash, full_name, phone, enabled, created_at, updated_at, created_by, updated_by)
VALUES ('customer@cineagent.dev', '$2a$10$CsJO04ssU7h00d7HOINSHu2dldEJBs51ccz3N7JMnnGxAYEDr6pzO', 'Sample Customer', '+910000000002', TRUE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, 'system', 'system');

INSERT INTO user_role (user_id, role)
SELECT id, 'CUSTOMER' FROM app_user WHERE email = 'customer@cineagent.dev';
