DROP TABLE IF EXISTS ddk_basket_item;
DROP TABLE IF EXISTS ddk_basket;

CREATE TABLE ddk_basket (
    id      BIGINT      NOT NULL PRIMARY KEY,
    owner   VARCHAR(20) NOT NULL,
    version BIGINT      NOT NULL DEFAULT 0
);

CREATE TABLE ddk_basket_item (
    id        BIGINT      NOT NULL AUTO_INCREMENT PRIMARY KEY,
    basket_id BIGINT      NOT NULL,
    sku       VARCHAR(20) NOT NULL,
    quantity  INT         NOT NULL
);
