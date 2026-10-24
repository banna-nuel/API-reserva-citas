CREATE TABLE users (
    id            BIGINT       NOT NULL AUTO_INCREMENT,
    full_name     VARCHAR(120) NOT NULL,
    email         VARCHAR(150) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,                 -- hash BCrypt
    phone         VARCHAR(30),
    role          VARCHAR(20)  NOT NULL,                 -- ADMIN | PROFESSIONAL | CLIENT
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at    DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_users_email UNIQUE (email)
);

CREATE TABLE professionals (
    id         BIGINT       NOT NULL AUTO_INCREMENT,
    user_id    BIGINT       NOT NULL,
    specialty  VARCHAR(100) NOT NULL,
    bio        VARCHAR(500),
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6)  NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_professionals_user UNIQUE (user_id),
    CONSTRAINT fk_professionals_user FOREIGN KEY (user_id) REFERENCES users (id)
);

CREATE TABLE service_offerings (
    id               BIGINT        NOT NULL AUTO_INCREMENT,
    name             VARCHAR(100)  NOT NULL,
    description      VARCHAR(500),
    duration_minutes INT           NOT NULL,
    price            DECIMAL(10,2) NOT NULL,
    active           BOOLEAN       NOT NULL DEFAULT TRUE,
    created_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at       DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT uq_service_offerings_name UNIQUE (name),
    CONSTRAINT ck_service_duration CHECK (duration_minutes > 0),
    CONSTRAINT ck_service_price CHECK (price >= 0)
);

CREATE TABLE professional_services (                     -- relación N:M
    professional_id BIGINT NOT NULL,
    service_id      BIGINT NOT NULL,
    PRIMARY KEY (professional_id, service_id),
    CONSTRAINT fk_ps_professional FOREIGN KEY (professional_id) REFERENCES professionals (id),
    CONSTRAINT fk_ps_service      FOREIGN KEY (service_id)      REFERENCES service_offerings (id)
);

CREATE TABLE working_hours (                             -- varios bloques por día (p. ej. pausa de almuerzo)
    id              BIGINT      NOT NULL AUTO_INCREMENT,
    professional_id BIGINT      NOT NULL,
    day_of_week     VARCHAR(10) NOT NULL,                -- MONDAY … SUNDAY (java.time.DayOfWeek)
    start_time      TIME        NOT NULL,
    end_time        TIME        NOT NULL,
    PRIMARY KEY (id),
    CONSTRAINT ck_wh_range CHECK (end_time > start_time),
    CONSTRAINT fk_wh_professional FOREIGN KEY (professional_id) REFERENCES professionals (id),
    INDEX idx_wh_professional_day (professional_id, day_of_week)
);

CREATE TABLE appointments (
    id              BIGINT        NOT NULL AUTO_INCREMENT,
    client_id       BIGINT        NOT NULL,
    professional_id BIGINT        NOT NULL,
    service_id      BIGINT        NOT NULL,
    start_time      DATETIME      NOT NULL,
    end_time        DATETIME      NOT NULL,
    status          VARCHAR(20)   NOT NULL DEFAULT 'PENDING',
    price           DECIMAL(10,2) NOT NULL,              -- precio congelado al reservar (RN-09)
    notes           VARCHAR(500),
    cancel_reason   VARCHAR(255),
    created_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at      DATETIME(6)   NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    CONSTRAINT ck_appt_range CHECK (end_time > start_time),
    CONSTRAINT fk_appt_client       FOREIGN KEY (client_id)       REFERENCES users (id),
    CONSTRAINT fk_appt_professional FOREIGN KEY (professional_id) REFERENCES professionals (id),
    CONSTRAINT fk_appt_service      FOREIGN KEY (service_id)      REFERENCES service_offerings (id),
    INDEX idx_appt_professional_time (professional_id, start_time, end_time),
    INDEX idx_appt_client_time (client_id, start_time),
    INDEX idx_appt_status (status)
);
