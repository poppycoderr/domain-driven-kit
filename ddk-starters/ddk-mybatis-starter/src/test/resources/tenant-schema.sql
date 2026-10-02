CREATE TABLE t_article
(
    id          BIGINT PRIMARY KEY,
    title       VARCHAR(100) NOT NULL,
    tenant_id   BIGINT       NOT NULL,
    create_by   VARCHAR(50),
    update_by   VARCHAR(50),
    create_time TIMESTAMP,
    update_time TIMESTAMP
);

CREATE TABLE t_dict
(
    id    BIGINT PRIMARY KEY,
    label VARCHAR(100) NOT NULL
);
