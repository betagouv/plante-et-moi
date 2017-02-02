# --- !Ups
CREATE TABLE review (
    application_id character varying(100) NOT NULL,
    agent_id character varying(100) NOT NULL,
    creation_date date NOT NULL,
    favorable boolean NOT NULL,
    comment text NOT NULL,
    PRIMARY KEY (application_id, agent_id)
);

CREATE TABLE application_extra (
    application_id character varying(100) NOT NULL,
    status character varying(100) NOT NULL,
    PRIMARY KEY (application_id)
);

# --- !Downs
DROP TABLE application_extra;
DROP TABLE review;
