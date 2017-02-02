# --- !Ups

CREATE TABLE application_extra (
    application_id character(100) NOT NULL,
    status character varying(100) NOT NULL,
    PRIMARY KEY (application_id)
);

# --- !Downs
DROP TABLE application_extra;