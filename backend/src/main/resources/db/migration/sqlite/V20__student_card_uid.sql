-- Physical PVC card support. The QR/barcode printed on a card carries the student's existing
-- pass_token, so printed cards need no new column. An RFID/NFC card is different: the chip emits a
-- fixed UID that the reader types like a keyboard, and we cannot choose what that UID is — so the
-- card has to be bound to the student after the fact, and that UID is what this column stores.
-- Uppercased on write so readers that emit lowercase hex still match.
ALTER TABLE students ADD COLUMN card_uid TEXT;
CREATE UNIQUE INDEX idx_students_card_uid ON students(card_uid) WHERE card_uid IS NOT NULL;
