# --- !Ups
CREATE TABLE application (
    id character varying(100) NOT NULL,
    city character varying(100) NOT NULL,
    status character varying(100) NOT NULL,
    name character varying(100) NOT NULL,
    email character varying(150) NOT NULL,
    type character varying(50) NOT NULL,
    address character varying(500) NOT NULL,
    creation_date date NOT NULL,
    coordinates point NOT NULL,
    phone character varying(50) NULL,
    fields json NOT NULL,
    files json NOT NULL,
    PRIMARY KEY (id)
);

# --- !Downs
DROP TABLE application;
