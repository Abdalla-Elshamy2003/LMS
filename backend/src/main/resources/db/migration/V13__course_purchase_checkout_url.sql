-- Real gateway checkout link for a purchase order (null while no live gateway is configured,
-- in which case the existing demo/sandbox pay flow applies instead).
ALTER TABLE course_purchase_orders ADD COLUMN checkout_url TEXT;
