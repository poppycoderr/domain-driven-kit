CREATE TABLE t_order
(
    id        BIGINT       NOT NULL,
    tenant_id BIGINT       NOT NULL,
    state     VARCHAR(50)  NOT NULL,
    PRIMARY KEY (tenant_id, id)
);

CREATE TABLE t_order_view
(
    tenant_id BIGINT      NOT NULL,
    id        BIGINT      NOT NULL,
    state     VARCHAR(50) NOT NULL,
    PRIMARY KEY (tenant_id, id)
);
