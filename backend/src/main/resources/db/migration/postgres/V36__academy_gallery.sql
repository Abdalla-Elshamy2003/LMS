-- A teacher's promotional posters, shown in full on their public page. The list holds /api/public/images/{id} URLs in
-- display order; the images themselves live in public_images (in the database, like every other page image).
ALTER TABLE teacher_academies ADD COLUMN gallery_json TEXT NOT NULL DEFAULT '[]';
