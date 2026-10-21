CREATE TABLE adventure (
    max_player integer NOT NULL,
    creator_id uuid NOT NULL,
    id uuid NOT NULL,
    description character varying(5000) NOT NULL,
    city character varying(255),
    invitation_code character varying(255),
    name character varying(255) NOT NULL,
    postal_code character varying(255),
    status character varying(255) NOT NULL,
    visibility character varying(255),
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT adventure_status_check CHECK (((status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'ONGOING'::character varying, 'ABORT'::character varying, 'FINISH'::character varying])::text[]))),
    CONSTRAINT adventure_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PRIVATE'::character varying, 'PUBLIC'::character varying])::text[])))
);

CREATE TABLE adventure_note (
    date date NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    content character varying(5000) NOT NULL,
    visibility character varying(255) NOT NULL,
    CONSTRAINT adventure_note_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PRIVATE'::character varying, 'PUBLIC'::character varying])::text[])))
);

CREATE TABLE adventure_players (
    adventure_id uuid NOT NULL,
    player_id uuid
);

CREATE TABLE "character" (
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    personality character varying(5000) NOT NULL,
    background character varying(80000) NOT NULL,
    martial_class character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    picture_path character varying(255) NOT NULL,
    race character varying(255) NOT NULL
);

CREATE TABLE disponibility (
    id uuid NOT NULL,
    planning_id uuid NOT NULL,
    account_id uuid NOT NULL
);

CREATE TABLE disponibility_dates (
    disponibility_dates date,
    disponibility_id uuid NOT NULL
);

CREATE TABLE historical_note (
    date date NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    content character varying(5000) NOT NULL
);

CREATE TABLE message (
    send_date timestamp(6) without time zone NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    sender_id uuid NOT NULL,
    content character varying(5000) NOT NULL
);

CREATE TABLE planning (
    adventure_id uuid NOT NULL,
    id uuid NOT NULL
);

CREATE TABLE propositions (
    proposition_dates date,
    planning_id uuid NOT NULL
);

CREATE TABLE rating (
    rate integer NOT NULL,
    emitter_id uuid NOT NULL,
    id uuid NOT NULL,
    target_id uuid NOT NULL
);

CREATE TABLE spell_book_entity_spell_list (
    spell_book_entity_id uuid NOT NULL,
    spell_list character varying(255)
);

CREATE TABLE spellbook (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    description character varying(5000),
    name character varying(255) NOT NULL
);

CREATE TABLE accounts (
    activated boolean NOT NULL,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    pseudo character varying(255) NOT NULL,
    verification_code character varying(255),
    -- A verification code expires; a null date means the code is not trusted.
    verification_expires_at timestamp(6) with time zone,
    -- Brute-force protection: consecutive failures and lock expiry.
    failed_login_attempts integer NOT NULL DEFAULT 0,
    locked_until timestamp(6) with time zone,
    -- A deleted account is anonymized, not removed: its characters, messages and votes keep pointing to it.
    anonymized_at timestamp(6) with time zone
);

ALTER TABLE adventure_note
    ADD CONSTRAINT adventure_note_pkey PRIMARY KEY (id);

ALTER TABLE adventure
    ADD CONSTRAINT adventure_pkey PRIMARY KEY (id);

ALTER TABLE "character"
    ADD CONSTRAINT character_pkey PRIMARY KEY (id);

ALTER TABLE disponibility
    ADD CONSTRAINT disponibility_pkey PRIMARY KEY (id);

ALTER TABLE historical_note
    ADD CONSTRAINT historical_note_pkey PRIMARY KEY (id);

ALTER TABLE message
    ADD CONSTRAINT message_pkey PRIMARY KEY (id);

ALTER TABLE planning
    ADD CONSTRAINT planning_adventure_id_key UNIQUE (adventure_id);

ALTER TABLE planning
    ADD CONSTRAINT planning_pkey PRIMARY KEY (id);

ALTER TABLE rating
    ADD CONSTRAINT rating_pkey PRIMARY KEY (id);

ALTER TABLE spellbook
    ADD CONSTRAINT spellbook_pkey PRIMARY KEY (id);

ALTER TABLE accounts
    ADD CONSTRAINT accounts_email_key UNIQUE (email);

ALTER TABLE accounts
    ADD CONSTRAINT accounts_pkey PRIMARY KEY (id);

ALTER TABLE accounts
    ADD CONSTRAINT accounts_pseudo_key UNIQUE (pseudo);

ALTER TABLE spellbook
    ADD CONSTRAINT fk_spellbook_account FOREIGN KEY (account_id) REFERENCES accounts(id);

ALTER TABLE "character"
    ADD CONSTRAINT fk_character_account FOREIGN KEY (account_id) REFERENCES accounts(id);

ALTER TABLE spell_book_entity_spell_list
    ADD CONSTRAINT fk_spell_list_spellbook FOREIGN KEY (spell_book_entity_id) REFERENCES spellbook(id);

ALTER TABLE disponibility_dates
    ADD CONSTRAINT fk_disponibility_dates_disponibility FOREIGN KEY (disponibility_id) REFERENCES disponibility(id);

ALTER TABLE adventure_players
    ADD CONSTRAINT fk_adventure_players_adventure FOREIGN KEY (adventure_id) REFERENCES adventure(id);

ALTER TABLE adventure_note
    ADD CONSTRAINT fk_adventure_note_adventure FOREIGN KEY (adventure_id) REFERENCES adventure(id);

ALTER TABLE propositions
    ADD CONSTRAINT fk_propositions_planning FOREIGN KEY (planning_id) REFERENCES planning(id);

ALTER TABLE historical_note
    ADD CONSTRAINT fk_historical_note_adventure FOREIGN KEY (adventure_id) REFERENCES adventure(id);

ALTER TABLE "character"
    ADD CONSTRAINT fk_character_adventure FOREIGN KEY (adventure_id) REFERENCES adventure(id);

-- Sessions: one per login. An access token is only valid while its session is.
CREATE TABLE sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone
);

-- Refresh tokens: single use, only a hash of the secret is stored.
CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp(6) with time zone NOT NULL,
    used_at timestamp(6) with time zone
);

CREATE INDEX idx_adventure_creator ON adventure (creator_id);
CREATE INDEX idx_adventure_players_adventure ON adventure_players (adventure_id);
CREATE INDEX idx_adventure_players_player ON adventure_players (player_id);
CREATE INDEX idx_adventure_note_adventure ON adventure_note (adventure_id);
CREATE INDEX idx_historical_note_adventure ON historical_note (adventure_id);
CREATE INDEX idx_character_adventure ON "character" (adventure_id);
CREATE INDEX idx_character_account ON "character" (account_id);
CREATE INDEX idx_message_adventure ON message (adventure_id);
CREATE INDEX idx_disponibility_planning ON disponibility (planning_id);
CREATE INDEX idx_spellbook_account ON spellbook (account_id);
CREATE INDEX idx_rating_target ON rating (target_id);
CREATE INDEX idx_sessions_account ON sessions (account_id);
CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);
