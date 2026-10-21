-- The game master can change: they can step down, hand over by invitation, or be replaced by a vote of the players.

-- Set while the adventure has no game master (they stepped down or deleted their account).
ALTER TABLE adventure ADD COLUMN master_vacant_since timestamp(6) with time zone;

-- A player asks for the role; the other players vote.
CREATE TABLE master_claims (
    id uuid PRIMARY KEY,
    adventure_id uuid NOT NULL REFERENCES adventure (id),
    candidate_id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    status varchar(20) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT master_claims_status_check CHECK (status IN ('OPEN', 'ACCEPTED', 'REJECTED', 'EXPIRED', 'CANCELLED'))
);

-- One vote at a time per adventure, whatever happens at the same moment.
CREATE UNIQUE INDEX uq_master_claims_open ON master_claims (adventure_id) WHERE status = 'OPEN';

CREATE TABLE master_claim_votes (
    claim_id uuid NOT NULL REFERENCES master_claims (id) ON DELETE CASCADE,
    voter_id uuid NOT NULL,
    approve boolean NOT NULL,
    PRIMARY KEY (claim_id, voter_id)
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
