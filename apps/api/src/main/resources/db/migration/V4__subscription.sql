-- The Stripe subscription of an account. A row exists as soon as the account has a Stripe customer;
-- status stays null until a subscription is known.
CREATE TABLE subscription (
    account_id uuid NOT NULL,
    stripe_customer_id character varying(255) NOT NULL,
    stripe_subscription_id character varying(255),
    plan_id character varying(50),
    billing_interval character varying(20),
    status character varying(30),
    current_period_end timestamp with time zone,
    cancel_at_period_end boolean NOT NULL DEFAULT false,
    updated_at timestamp with time zone NOT NULL,
    CONSTRAINT subscription_pkey PRIMARY KEY (account_id),
    CONSTRAINT subscription_stripe_customer_key UNIQUE (stripe_customer_id),
    CONSTRAINT subscription_stripe_subscription_key UNIQUE (stripe_subscription_id),
    CONSTRAINT fk_subscription_account FOREIGN KEY (account_id) REFERENCES accounts (id)
);
