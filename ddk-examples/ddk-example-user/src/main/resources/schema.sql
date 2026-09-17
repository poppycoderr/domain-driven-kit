CREATE TABLE IF NOT EXISTS t_user
(
    id           BIGINT PRIMARY KEY,
    username     VARCHAR(20)  NOT NULL,
    password     VARCHAR(100) NOT NULL,
    gender       INT          NOT NULL,
    phone_number VARCHAR(11)  NOT NULL,
    email        VARCHAR(100),
    status       BOOLEAN      NOT NULL,
    version      BIGINT       NOT NULL DEFAULT 0,
    CONSTRAINT uk_user_username UNIQUE (username)
);
