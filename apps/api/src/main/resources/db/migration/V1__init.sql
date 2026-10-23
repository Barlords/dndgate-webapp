-- The whole schema of DnDGate. It replaces the migrations V1 to V18, compacted once no database held data any more.

-- ---------------------------------------------------------------------------------------------------------------
-- Accounts
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE accounts (
    activated boolean NOT NULL,
    id uuid NOT NULL,
    email character varying(255) NOT NULL,
    password character varying(255) NOT NULL,
    pseudo character varying(255) NOT NULL,
    -- A deleted account is anonymized, not removed: its characters, messages and votes keep pointing to it.
    anonymized_at timestamp(6) with time zone,
    -- A new e-mail address only replaces the current one once its owner followed the link sent to it.
    -- Until then it waits here, with its own code (never the registration code) and its own expiry.
    pending_email character varying(255),
    pending_email_code character varying(255),
    pending_email_expires_at timestamp with time zone,
    -- Every row that can be edited carries a version, checked and increased by each update: a write made from a
    -- stale read is refused instead of erasing the change made meanwhile.
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT accounts_pkey PRIMARY KEY (id),
    CONSTRAINT accounts_email_key UNIQUE (email),
    CONSTRAINT accounts_pseudo_key UNIQUE (pseudo)
);

-- A pseudo, and an e-mail address, designate exactly one account whatever their case ("Gandalf" and "gandalf" are
-- the same person): accounts are found by pseudo (invitations, login) and by address.
CREATE UNIQUE INDEX accounts_pseudo_lower_key ON accounts (lower(pseudo));
CREATE UNIQUE INDEX accounts_email_lower_key ON accounts (lower(email));
-- The link of an address change carries the new address: it is how the pending change is found.
CREATE INDEX accounts_pending_email_idx ON accounts (pending_email);

-- Signups waiting for the click on the link of the confirmation mail: POST /accounts records one of these instead of
-- creating an account, and the account only exists once the owner of the address confirmed. The address is the
-- normalised one (trimmed, lower-cased); the code is kept as a SHA-256 digest, never in clear. A signup is only
-- useful until it expires: the nightly purge removes the older ones.
CREATE TABLE pending_signups (
    id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE,
    pseudo text NOT NULL,
    password_hash text NOT NULL,
    code_hash text NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_pending_signups_expires_at ON pending_signups (expires_at);

-- Notices sent to the owner of an address somebody tried to register with: what the "one notice per address per
-- window" limit counts. The address is kept as a SHA-256 digest; the nightly purge removes the older rows.
CREATE TABLE signup_notices (
    email_hash varchar(64) PRIMARY KEY,
    sent_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_signup_notices_sent_at ON signup_notices (sent_at);

-- Sessions: one per login. An access token is only valid while its session is.
CREATE TABLE sessions (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    revoked_at timestamp(6) with time zone
);

CREATE INDEX idx_sessions_account ON sessions (account_id);

-- Refresh tokens: single use, only a hash of the secret is stored.
CREATE TABLE refresh_tokens (
    id uuid PRIMARY KEY,
    session_id uuid NOT NULL REFERENCES sessions (id) ON DELETE CASCADE,
    token_hash varchar(64) NOT NULL UNIQUE,
    expires_at timestamp(6) with time zone NOT NULL,
    used_at timestamp(6) with time zone
);

CREATE INDEX idx_refresh_tokens_session ON refresh_tokens (session_id);

-- The lockout counts the wrong passwords per (account, client): pseudos are public, so a lock shared by every client
-- would let anybody keep a stranger out of their account. client_hash is a SHA-256 of the client address, never the
-- address itself. A row is only useful for the length of the lock window; the nightly purge removes the older ones.
CREATE TABLE login_failures (
    id uuid PRIMARY KEY,
    account_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    client_hash varchar(64) NOT NULL,
    failed_attempts integer NOT NULL,
    locked_until timestamp(6) with time zone,
    last_failed_at timestamp(6) with time zone NOT NULL,
    CONSTRAINT uq_login_failures_client UNIQUE (account_id, client_hash)
);

CREATE INDEX idx_login_failures_last_failed_at ON login_failures (last_failed_at);

-- ---------------------------------------------------------------------------------------------------------------
-- Adventures
-- ---------------------------------------------------------------------------------------------------------------

-- No ON DELETE towards accounts or adventures in this part: accounts are never deleted (they are anonymized), and the
-- code deletes the children of an adventure explicitly, so that a forgotten child fails loudly instead of disappearing
-- silently.
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
    visibility character varying(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    -- Set while the adventure has no game master (they stepped down or deleted their account).
    master_vacant_since timestamp(6) with time zone,
    CONSTRAINT adventure_pkey PRIMARY KEY (id),
    CONSTRAINT adventure_status_check CHECK (((status)::text = ANY ((ARRAY['NOT_STARTED'::character varying, 'ONGOING'::character varying, 'ABORT'::character varying, 'FINISH'::character varying])::text[]))),
    CONSTRAINT adventure_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PRIVATE'::character varying, 'PUBLIC'::character varying])::text[]))),
    -- Checks followed by writes (join, rate, generate a code) are two requests: the database has the last word.
    CONSTRAINT uq_adventure_invitation_code UNIQUE (invitation_code),
    CONSTRAINT fk_adventure_creator FOREIGN KEY (creator_id) REFERENCES accounts (id)
);

CREATE INDEX idx_adventure_creator ON adventure (creator_id);

CREATE TABLE adventure_players (
    adventure_id uuid NOT NULL,
    player_id uuid NOT NULL,
    CONSTRAINT uq_adventure_players UNIQUE (adventure_id, player_id),
    CONSTRAINT fk_adventure_players_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id),
    CONSTRAINT fk_adventure_players_player FOREIGN KEY (player_id) REFERENCES accounts (id)
);

CREATE INDEX idx_adventure_players_adventure ON adventure_players (adventure_id);
CREATE INDEX idx_adventure_players_player ON adventure_players (player_id);

-- Invitation e-mails sent to players: what the per-sender and per-recipient limits count.
-- A row is only useful for the length of the longest limit window; the nightly purge removes the older ones.
CREATE TABLE sent_invitations (
    id uuid PRIMARY KEY,
    sender_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    recipient_id uuid NOT NULL REFERENCES accounts (id) ON DELETE CASCADE,
    adventure_id uuid NOT NULL REFERENCES adventure (id) ON DELETE CASCADE,
    sent_at timestamp(6) with time zone NOT NULL
);

CREATE INDEX idx_sent_invitations_sender ON sent_invitations (sender_id, sent_at);
CREATE INDEX idx_sent_invitations_recipient ON sent_invitations (recipient_id, adventure_id, sent_at);
CREATE INDEX idx_sent_invitations_sent_at ON sent_invitations (sent_at);

-- ---------------------------------------------------------------------------------------------------------------
-- Game master: step down, hand over by invitation, or be replaced by a vote of the players
-- ---------------------------------------------------------------------------------------------------------------

-- A player asks for the role; the other players vote.
CREATE TABLE master_claims (
    id uuid PRIMARY KEY,
    adventure_id uuid NOT NULL REFERENCES adventure (id),
    candidate_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    status varchar(20) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT master_claims_status_check CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT fk_master_claims_candidate FOREIGN KEY (candidate_id) REFERENCES accounts (id)
);

-- One vote at a time per adventure, whatever happens at the same moment.
CREATE UNIQUE INDEX uq_master_claims_open ON master_claims (adventure_id) WHERE status = 'OPEN';

CREATE TABLE master_claim_votes (
    claim_id uuid NOT NULL REFERENCES master_claims (id) ON DELETE CASCADE,
    voter_id uuid NOT NULL,
    approve boolean NOT NULL,
    PRIMARY KEY (claim_id, voter_id),
    CONSTRAINT fk_master_claim_votes_voter FOREIGN KEY (voter_id) REFERENCES accounts (id)
);

-- The code a game master gives to someone who will take over (only a hash is kept), one at a time per adventure.
CREATE TABLE master_invitations (
    id uuid PRIMARY KEY,
    adventure_id uuid NOT NULL UNIQUE REFERENCES adventure (id),
    code_hash varchar(64) NOT NULL UNIQUE,
    created_by uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL
);

-- ---------------------------------------------------------------------------------------------------------------
-- Inside an adventure: characters, notes, messages, planning
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE "character" (
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    personality character varying(5000) NOT NULL,
    background character varying(80000) NOT NULL,
    martial_class character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    picture_path character varying(255) NOT NULL,
    race character varying(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT character_pkey PRIMARY KEY (id),
    CONSTRAINT fk_character_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id),
    CONSTRAINT fk_character_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_character_adventure ON "character" (adventure_id);
CREATE INDEX idx_character_account ON "character" (account_id);

-- The game master's notes; PRIVATE ones are only read by the game master.
CREATE TABLE adventure_note (
    date date NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    content character varying(5000) NOT NULL,
    visibility character varying(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT adventure_note_pkey PRIMARY KEY (id),
    CONSTRAINT adventure_note_visibility_check CHECK (((visibility)::text = ANY ((ARRAY['PRIVATE'::character varying, 'PUBLIC'::character varying])::text[]))),
    CONSTRAINT fk_adventure_note_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id)
);

CREATE INDEX idx_adventure_note_adventure ON adventure_note (adventure_id);

-- The chronicle of the adventure.
CREATE TABLE historical_note (
    date date NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    content character varying(5000) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT historical_note_pkey PRIMARY KEY (id),
    CONSTRAINT fk_historical_note_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id)
);

CREATE INDEX idx_historical_note_adventure ON historical_note (adventure_id);

-- The send date of a message is fixed by the server as an instant.
CREATE TABLE message (
    send_date timestamp(6) with time zone NOT NULL,
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    sender_id uuid NOT NULL,
    content character varying(5000) NOT NULL,
    CONSTRAINT message_pkey PRIMARY KEY (id),
    CONSTRAINT fk_message_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id),
    CONSTRAINT fk_message_sender FOREIGN KEY (sender_id) REFERENCES accounts (id)
);

-- Messages of an adventure, newest first (also serves lookups by adventure alone).
CREATE INDEX message_adventure_send_date_idx ON message (adventure_id, send_date DESC);
-- The message rate limit counts the messages one account sent during the last minute.
CREATE INDEX message_sender_send_date_idx ON message (sender_id, send_date DESC);

CREATE TABLE planning (
    adventure_id uuid NOT NULL,
    id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT planning_pkey PRIMARY KEY (id),
    CONSTRAINT planning_adventure_id_key UNIQUE (adventure_id),
    CONSTRAINT fk_planning_adventure FOREIGN KEY (adventure_id) REFERENCES adventure (id)
);

-- The collection tables (proposed dates, availability dates, spell list) keep no primary key on purpose: a list may
-- hold the same value twice.
CREATE TABLE propositions (
    proposition_dates date,
    planning_id uuid NOT NULL,
    CONSTRAINT fk_propositions_planning FOREIGN KEY (planning_id) REFERENCES planning (id)
);

-- One vote per account and planning.
CREATE TABLE availability (
    id uuid NOT NULL,
    planning_id uuid NOT NULL,
    account_id uuid NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT availability_pkey PRIMARY KEY (id),
    CONSTRAINT fk_availability_planning FOREIGN KEY (planning_id) REFERENCES planning (id),
    CONSTRAINT fk_availability_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_availability_planning ON availability (planning_id);
CREATE UNIQUE INDEX availability_planning_account_key ON availability (planning_id, account_id);

CREATE TABLE availability_dates (
    availability_dates date,
    availability_id uuid NOT NULL,
    CONSTRAINT fk_availability_dates_availability FOREIGN KEY (availability_id) REFERENCES availability (id)
);

-- ---------------------------------------------------------------------------------------------------------------
-- Ratings between players
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE rating (
    rate integer NOT NULL,
    emitter_id uuid NOT NULL,
    id uuid NOT NULL,
    target_id uuid NOT NULL,
    -- Gives the list a player received a meaningful order.
    created_at timestamp with time zone NOT NULL,
    CONSTRAINT rating_pkey PRIMARY KEY (id),
    CONSTRAINT uq_rating_emitter_target UNIQUE (emitter_id, target_id),
    CONSTRAINT fk_rating_emitter FOREIGN KEY (emitter_id) REFERENCES accounts (id),
    CONSTRAINT fk_rating_target FOREIGN KEY (target_id) REFERENCES accounts (id)
);

CREATE INDEX idx_rating_target ON rating (target_id);

-- ---------------------------------------------------------------------------------------------------------------
-- Spell books
-- ---------------------------------------------------------------------------------------------------------------

CREATE TABLE spellbook (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    description character varying(5000),
    name character varying(255) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    -- The rules the spell indexes belong to (e.g. dnd5e-2014, dnd5e-2024); a spell book never changes ruleset.
    ruleset character varying(50) NOT NULL,
    CONSTRAINT spellbook_pkey PRIMARY KEY (id),
    CONSTRAINT fk_spellbook_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_spellbook_account ON spellbook (account_id);

CREATE TABLE spellbook_spells (
    spellbook_id uuid NOT NULL,
    spell character varying(255),
    CONSTRAINT fk_spellbook_spells_spellbook FOREIGN KEY (spellbook_id) REFERENCES spellbook (id)
);
