CREATE TABLE t_contract
(
    id        BIGINT PRIMARY KEY,
    title     VARCHAR(100) NOT NULL,
    dept_id   BIGINT       NOT NULL,
    create_by BIGINT       NOT NULL
);

CREATE TABLE t_note
(
    id        BIGINT PRIMARY KEY,
    title     VARCHAR(100) NOT NULL,
    author    VARCHAR(50)  NOT NULL
);

CREATE TABLE t_region
(
    id   BIGINT PRIMARY KEY,
    name VARCHAR(100) NOT NULL
);
