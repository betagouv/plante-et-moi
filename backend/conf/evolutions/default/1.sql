# --- !Ups

CREATE TABLE review (
    application_id character(100) NOT NULL,
    agent_id character(100) NOT NULL,
    creation_date date NOT NULL,
    favorable boolean NOT NULL,
    comment text NOT NULL,
    PRIMARY KEY (application_id, agent_id)
);

# --- !Downs

DROP TABLE review;