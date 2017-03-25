# --- !Ups
CREATE TABLE setting (
    key character varying(100) NOT NULL,
    city character varying(100) NOT NULL,
    value json NOT NULL,
    PRIMARY KEY (key, city)
);

# --- !Downs
DROP TABLE setting;