---
name: manarah-public-courses
description: Create the proposed public course discovery page at /courses with editorial course cards, real filters, and a clear registration path.
---

# Public Course Discovery

## Functional responsibilities and clean implementation

Apply [the architecture contract](../manarah-clean-architecture/SKILL.md). The names below are proposed responsibility boundaries; reuse equivalent code and verify endpoint support before implementing them.

| Function / use case | Owner | Required contract |
| --- | --- | --- |
| loadPublicCatalog(tenantSlug, signal) | Shared public query adapter | Reuse the public landing DTO and avoid authenticated course endpoints. |
| normalizeCatalogFilters(searchParams) | Pure URL/query model | Validate supported subject, grade, teacher display value, and numeric range inputs. |
| selectPublicCourses(courses, filters, sort) | Pure selector | Filter/sort the complete public list before optional pagination and preserve stable course IDs. |
| buildRegistrationContext(courseId, tenantSlug, catalog) | Pure navigation model | Validate selection against the current tenant catalog and construct only the necessary registration context. |

**Architecture boundary.** Reuse the public read service and mapper shared with landing; keep private CourseDetail and public preview models separate. Course selection is navigation state, not enrollment persistence. Do not add local storage of private student fields for convenience.

**Behavioral verification.** Verify combined price/subject filtering, unknown query values, tenant switching, removed courses, and anonymous registration navigation without protected data requests.


Read [the shared design system](../manarah-design-system/SKILL.md). Status: proposed new page; `/courses` is not currently registered. Extract relevant content from `frontend/src/pages/Landing.jsx` and register the new route in `frontend/src/App.jsx` during implementation. Use `GET /api/public/landing`, not protected application endpoints.

## Layout and discovery

Create an editorial catalog with a large Arabic title, a short orientation line, a featured course composition, and an orderly grid. Feature a course only through an explicit editorial choice or stated sorting rule. Use returned coverUrl where appropriate, with a stable subject-based visual fallback. Keep card title, subject, grade level, teacherName, and price visible. Avoid invented enrollment counts, star ratings, lesson counts, or discounts.

Provide title search, subject, grade level, teacher name, and price-range filters using fields in the public response. Teacher names are display values, not stable IDs: the public course payload currently omits teacherId. Preserve that limitation when linking from the teacher directory. Sort the full public list before optional pagination. Show result count, removable chips, and Clear all. On mobile, retain search and a filter button with active count; advanced controls belong in a usable drawer.

## Conversion and motion

Each card should lead to an accessible course preview using available public fields or to `/register?courseId=...` with explicit action wording. Do not add a public detail route unless a corresponding page and skill are introduced. Do not send anonymous users to protected course materials. Preserve login access in the header and footer.

Use image framing, a short initial grid reveal, and restrained hover lift. Keep price stable and avoid auto-scrolling shelves. No chart is needed. The only form here is discovery; enrollment belongs to [public registration](../manarah-public-register/SKILL.md).

## Acceptance scenarios

- Direct `/courses` loading and refresh work after route implementation.
- Combined filters return correct counts and clearing them restores the full catalog.
- Registration receives a valid selected course; missing/removed courses are handled explicitly.
- Missing covers, duplicate teacher names, long titles, empty data, and API failure remain usable.
