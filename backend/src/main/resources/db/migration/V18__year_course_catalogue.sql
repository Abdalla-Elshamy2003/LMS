-- A catalogue of 8 courses for each school year (prep 1 → secondary 3), so a student who
-- signs up for a year immediately has that year's courses to browse on their dashboard.
-- Guarded by the branch lookup: if the tenant has no branch the SELECT yields no rows and
-- this migration is simply a no-op rather than failing the boot on a NOT NULL branch_id.

INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أساسيات الجبر والهندسة — الصف الأول الإعدادي', 'رياضيات', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 250, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'العلوم بالتجربة والفهم — الصف الأول الإعدادي', 'علوم', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج علوم مع تدريبات ومراجعات.', 240, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وقراءة وتعبير — الصف الأول الإعدادي', 'لغة عربية', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 220, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'قواعد ومحادثة خطوة بخطوة — الصف الأول الإعدادي', 'لغة إنجليزية', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تاريخ وجغرافيا مبسّطة — الصف الأول الإعدادي', 'دراسات اجتماعية', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج دراسات اجتماعية مع تدريبات ومراجعات.', 200, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'مهارات الحاسب والبرمجة — الصف الأول الإعدادي', 'حاسب آلي', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج حاسب آلي مع تدريبات ومراجعات.', 210, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'منهج مبسّط ومراجعات — الصف الأول الإعدادي', 'تربية دينية', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج تربية دينية مع تدريبات ومراجعات.', 180, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تأسيس من الصفر — الصف الأول الإعدادي', 'لغة فرنسية', 'إعدادي', 'الصف الأول الإعدادي', 'شرح منظّم لمنهج لغة فرنسية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أساسيات الجبر والهندسة — الصف الثاني الإعدادي', 'رياضيات', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 250, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'العلوم بالتجربة والفهم — الصف الثاني الإعدادي', 'علوم', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج علوم مع تدريبات ومراجعات.', 240, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وقراءة وتعبير — الصف الثاني الإعدادي', 'لغة عربية', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 220, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'قواعد ومحادثة خطوة بخطوة — الصف الثاني الإعدادي', 'لغة إنجليزية', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تاريخ وجغرافيا مبسّطة — الصف الثاني الإعدادي', 'دراسات اجتماعية', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج دراسات اجتماعية مع تدريبات ومراجعات.', 200, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'مهارات الحاسب والبرمجة — الصف الثاني الإعدادي', 'حاسب آلي', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج حاسب آلي مع تدريبات ومراجعات.', 210, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'منهج مبسّط ومراجعات — الصف الثاني الإعدادي', 'تربية دينية', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج تربية دينية مع تدريبات ومراجعات.', 180, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تأسيس من الصفر — الصف الثاني الإعدادي', 'لغة فرنسية', 'إعدادي', 'الصف الثاني الإعدادي', 'شرح منظّم لمنهج لغة فرنسية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أساسيات الجبر والهندسة — الصف الثالث الإعدادي', 'رياضيات', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 250, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'العلوم بالتجربة والفهم — الصف الثالث الإعدادي', 'علوم', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج علوم مع تدريبات ومراجعات.', 240, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وقراءة وتعبير — الصف الثالث الإعدادي', 'لغة عربية', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 220, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'قواعد ومحادثة خطوة بخطوة — الصف الثالث الإعدادي', 'لغة إنجليزية', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تاريخ وجغرافيا مبسّطة — الصف الثالث الإعدادي', 'دراسات اجتماعية', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج دراسات اجتماعية مع تدريبات ومراجعات.', 200, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'مهارات الحاسب والبرمجة — الصف الثالث الإعدادي', 'حاسب آلي', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج حاسب آلي مع تدريبات ومراجعات.', 210, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'منهج مبسّط ومراجعات — الصف الثالث الإعدادي', 'تربية دينية', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج تربية دينية مع تدريبات ومراجعات.', 180, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تأسيس من الصفر — الصف الثالث الإعدادي', 'لغة فرنسية', 'إعدادي', 'الصف الثالث الإعدادي', 'شرح منظّم لمنهج لغة فرنسية مع تدريبات ومراجعات.', 230, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الجبر والتفاضل والهندسة — الصف الأول الثانوي', 'رياضيات', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 400, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الفهم قبل القانون — الصف الأول الثانوي', 'فيزياء', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج فيزياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تفاعلات ومسائل — الصف الأول الثانوي', 'كيمياء', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج كيمياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'شرح مصوّر ومراجعة — الصف الأول الثانوي', 'أحياء', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج أحياء مع تدريبات ومراجعات.', 360, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وأدب وبلاغة — الصف الأول الثانوي', 'لغة عربية', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 300, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'Grammar و Translation — الصف الأول الثانوي', 'لغة إنجليزية', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 320, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'خرائط وأحداث مترابطة — الصف الأول الثانوي', 'تاريخ وجغرافيا', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج تاريخ وجغرافيا مع تدريبات ومراجعات.', 280, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أفكار واضحة وأمثلة — الصف الأول الثانوي', 'فلسفة ومنطق', 'ثانوي', 'الصف الأول الثانوي', 'شرح منظّم لمنهج فلسفة ومنطق مع تدريبات ومراجعات.', 260, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الجبر والتفاضل والهندسة — الصف الثاني الثانوي', 'رياضيات', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 400, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الفهم قبل القانون — الصف الثاني الثانوي', 'فيزياء', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج فيزياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تفاعلات ومسائل — الصف الثاني الثانوي', 'كيمياء', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج كيمياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'شرح مصوّر ومراجعة — الصف الثاني الثانوي', 'أحياء', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج أحياء مع تدريبات ومراجعات.', 360, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وأدب وبلاغة — الصف الثاني الثانوي', 'لغة عربية', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 300, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'Grammar و Translation — الصف الثاني الثانوي', 'لغة إنجليزية', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 320, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'خرائط وأحداث مترابطة — الصف الثاني الثانوي', 'تاريخ وجغرافيا', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج تاريخ وجغرافيا مع تدريبات ومراجعات.', 280, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أفكار واضحة وأمثلة — الصف الثاني الثانوي', 'فلسفة ومنطق', 'ثانوي', 'الصف الثاني الثانوي', 'شرح منظّم لمنهج فلسفة ومنطق مع تدريبات ومراجعات.', 260, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الجبر والتفاضل والهندسة — الصف الثالث الثانوي', 'رياضيات', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج رياضيات مع تدريبات ومراجعات.', 400, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'الفهم قبل القانون — الصف الثالث الثانوي', 'فيزياء', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج فيزياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'تفاعلات ومسائل — الصف الثالث الثانوي', 'كيمياء', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج كيمياء مع تدريبات ومراجعات.', 380, 'ACTIVE', '/images/course-1.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'شرح مصوّر ومراجعة — الصف الثالث الثانوي', 'أحياء', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج أحياء مع تدريبات ومراجعات.', 360, 'ACTIVE', '/images/course-2.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'نحو وأدب وبلاغة — الصف الثالث الثانوي', 'لغة عربية', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج لغة عربية مع تدريبات ومراجعات.', 300, 'ACTIVE', '/images/course-3.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'Grammar و Translation — الصف الثالث الثانوي', 'لغة إنجليزية', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج لغة إنجليزية مع تدريبات ومراجعات.', 320, 'ACTIVE', '/images/course-4.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'خرائط وأحداث مترابطة — الصف الثالث الثانوي', 'تاريخ وجغرافيا', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج تاريخ وجغرافيا مع تدريبات ومراجعات.', 280, 'ACTIVE', '/images/course-5.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
INSERT INTO courses (tenant_id, branch_id, teacher_id, title, subject, grade_level, grade, description, price, status, cover_url, created_at)
SELECT 1, b.id, NULL, 'أفكار واضحة وأمثلة — الصف الثالث الثانوي', 'فلسفة ومنطق', 'ثانوي', 'الصف الثالث الثانوي', 'شرح منظّم لمنهج فلسفة ومنطق مع تدريبات ومراجعات.', 260, 'ACTIVE', '/images/course-6.jpg', datetime('now')
FROM branches b WHERE b.tenant_id = 1 ORDER BY b.id LIMIT 1;
