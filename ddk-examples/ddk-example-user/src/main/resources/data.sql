-- 密码均为 password123
MERGE INTO t_user (id, username, password, gender, phone_number, email, status, version) KEY (id) VALUES
    (1001, 'alice', '$2a$10$hLgsrer5.EhjcRUkKfZZoez.NdO2GAFg1zdbvdGizDr6.gA44id0W', 1, '13800138001', 'alice@example.com', TRUE, 0),
    (1002, 'bobby', '$2a$10$hLgsrer5.EhjcRUkKfZZoez.NdO2GAFg1zdbvdGizDr6.gA44id0W', 0, '13800138002', NULL, TRUE, 0),
    (1003, 'carol', '$2a$10$hLgsrer5.EhjcRUkKfZZoez.NdO2GAFg1zdbvdGizDr6.gA44id0W', 1, '13800138003', 'carol@example.com', FALSE, 0);
