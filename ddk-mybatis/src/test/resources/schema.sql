DROP TABLE IF EXISTS ddk_user;

CREATE TABLE ddk_user (
    id           BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY,
    username     VARCHAR(20)  NOT NULL,
    gender       INT          NOT NULL,
    email        VARCHAR(64),
    status       BOOLEAN      NOT NULL DEFAULT TRUE,
    version      BIGINT       NOT NULL DEFAULT 0
);
