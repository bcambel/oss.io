
CREATE FUNCTION id_generator (OUT result bigint)  RETURNS bigint
  VOLATILE
AS $dbvis$
DECLARE
    our_epoch bigint := 1314220021721;
    seq_id bigint;
    now_millis bigint;
    -- the id of this DB shard, must be set for each
    -- schema shard you have - you could pass this as a parameter too
    shard_id int := 1;
BEGIN
    SELECT nextval('global_id_sequence') % 1024 INTO seq_id;

    SELECT FLOOR(EXTRACT(EPOCH FROM clock_timestamp()) * 1000) INTO now_millis;
    result := (now_millis - our_epoch) << 23;
    result := result | (shard_id << 10);
    result := result | (seq_id);
END;
$dbvis$ LANGUAGE plpgsql


CREATE TABLE user_identity (id CHARACTER VARYING(40) NOT NULL, identity JSON, created_at TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT now(), updated_at TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT now(), PRIMARY KEY (id));
CREATE TABLE user_token_link (user_id CHARACTER VARYING(40), user_token CHARACTER VARYING(36), UNIQUE (user_id, user_token));
CREATE TABLE oss_user (id BIGINT DEFAULT id_generator() NOT NULL, email TEXT, name TEXT, img TEXT, created_at TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT now(), updated_at TIMESTAMP(6) WITHOUT TIME ZONE DEFAULT now(), PRIMARY KEY (id), UNIQUE (email));