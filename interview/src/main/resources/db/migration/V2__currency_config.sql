CREATE TABLE currency_config (
    code                 varchar(3) PRIMARY KEY,
    minor_units_per_unit bigint     NOT NULL CHECK (minor_units_per_unit > 0)
);
INSERT INTO currency_config VALUES ('ETB', 100);
