CREATE TABLE t_deal
(
    id        BIGINT PRIMARY KEY,
    title     VARCHAR(100) NOT NULL,
    tenant_id BIGINT       NOT NULL,
    dept_id   BIGINT       NOT NULL,
    create_by BIGINT       NOT NULL,
    version   BIGINT       NOT NULL DEFAULT 0
);

CREATE TABLE t_dept
(
    id        BIGINT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL,
    tenant_id BIGINT       NOT NULL
);
