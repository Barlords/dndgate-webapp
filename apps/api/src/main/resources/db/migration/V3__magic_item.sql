-- The magic items of an account: personal, like the spell books.
CREATE TABLE magic_item (
    id uuid NOT NULL,
    account_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    type character varying(50) NOT NULL,
    rarity character varying(50) NOT NULL,
    requires_attunement boolean NOT NULL,
    description character varying(5000) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    CONSTRAINT magic_item_pkey PRIMARY KEY (id),
    CONSTRAINT magic_item_type_check CHECK (type IN ('ARMOR', 'POTION', 'RING', 'ROD', 'SCROLL', 'STAFF', 'WAND',
        'WEAPON', 'WONDROUS_ITEM')),
    CONSTRAINT magic_item_rarity_check CHECK (rarity IN ('COMMON', 'UNCOMMON', 'RARE', 'VERY_RARE', 'LEGENDARY',
        'ARTIFACT')),
    CONSTRAINT fk_magic_item_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);

CREATE INDEX idx_magic_item_account ON magic_item (account_id);
