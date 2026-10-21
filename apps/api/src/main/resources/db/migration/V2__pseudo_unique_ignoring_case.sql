-- A pseudo designates exactly one account, whatever its case ("Gandalf" and "gandalf" are the same person).
-- Accounts are found by pseudo (invitations, login): two pseudos differing only by case would be ambiguous.
CREATE UNIQUE INDEX accounts_pseudo_lower_key ON accounts (lower(pseudo));
