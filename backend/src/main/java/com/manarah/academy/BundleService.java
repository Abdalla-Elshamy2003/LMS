package com.manarah.academy;

import com.manarah.common.exception.ApiExceptions.*;
import com.manarah.course.repo.CourseRepository;
import com.manarah.security.UserPrincipal;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Teacher packages. They are managed by head-office admins (the tenant that manages the academies), and a member
 * may only link an academy that head office manages. The public side shows published packages only, and a
 * member's teacher page, videos and courses only while that academy is itself published.
 */
@Service
public class BundleService {
    private static final int MAX_MEMBERS = 12;
    private static final Pattern SLUG = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern PHOTO = Pattern.compile("^(/images/bundles/[a-z0-9._-]+\\.(jpg|jpeg|png|webp)|/api/public/images/[0-9]+|https://\\S+)$");
    /** Same rule as the academy video playlist. */
    private static final Pattern VIDEO = Pattern.compile("^(https://\\S+|/videos/[a-zA-Z0-9._-]+\\.mp4)$");

    private final TeacherBundleRepository bundles;
    private final TeacherBundleMemberRepository members;
    private final TeacherAcademyRepository academies;
    private final CourseRepository courses;
    private final com.manarah.audit.AuditService audit;

    public BundleService(TeacherBundleRepository bundles, TeacherBundleMemberRepository members, TeacherAcademyRepository academies,
                         CourseRepository courses, com.manarah.audit.AuditService audit) {
        this.bundles = bundles; this.members = members; this.academies = academies; this.courses = courses; this.audit = audit;
    }

    public record MemberRequest(Long academyId, String displayName, String subject, String photoUrl, String introVideoUrl) {}
    public record BundleRequest(String slug, String name, String tagline, String description, Boolean published,
                                Integer sortOrder, List<MemberRequest> members) {}

    // ---- Public ------------------------------------------------------------------------------------------------

    /** Cards for the home page: enough to draw the package's portraits and subjects. */
    public List<Map<String, Object>> publicCards() {
        return bundles.findByPublishedTrueOrderBySortOrderAscIdAsc().stream().map(b -> {
            Map<String, Object> card = new LinkedHashMap<>();
            card.put("slug", b.getSlug()); card.put("name", b.getName()); card.put("tagline", b.getTagline());
            card.put("members", members.findByBundleIdOrderByPositionAsc(b.getId()).stream().map(m -> {
                var a = publishedAcademy(m);
                return Map.<String, Object>of("name", name(m, a), "subject", m.getSubject(), "photoUrl", photo(m, a));
            }).toList());
            return card;
        }).toList();
    }

    /** The package landing page: every member with their teacher page, videos and courses when they have one. */
    public Map<String, Object> publicLanding(String slug) {
        var b = bundles.findBySlug(slug).filter(TeacherBundle::isPublished)
                .orElseThrow(() -> new NotFoundException("الباقة غير متاحة"));
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("slug", b.getSlug()); out.put("name", b.getName()); out.put("tagline", b.getTagline()); out.put("description", b.getDescription());
        out.put("members", members.findByBundleIdOrderByPositionAsc(b.getId()).stream().map(m -> {
            var a = publishedAcademy(m);
            Map<String, Object> v = new LinkedHashMap<>();
            v.put("id", m.getId()); v.put("name", name(m, a)); v.put("subject", m.getSubject());
            v.put("photoUrl", photo(m, a)); v.put("introVideoUrl", m.getIntroVideoUrl());
            if (a == null) { v.put("teacher", null); return v; }
            var taught = courses.findByTenantIdAndTeacherId(a.getTenantId(), a.getTeacherId()).stream()
                    .filter(c -> "ACTIVE".equals(c.getStatus())).toList();
            Map<String, Object> t = new LinkedHashMap<>();
            t.put("slug", a.getSlug()); t.put("name", a.getName()); t.put("tagline", Objects.toString(a.getTagline(), ""));
            t.put("headline", Objects.toString(a.getHeadline(), "")); t.put("description", Objects.toString(a.getDescription(), ""));
            t.put("videos", a.getVideos());
            t.put("courses", taught.stream().map(c -> Map.<String, Object>of("id", c.getId(), "title", c.getTitle(),
                    "grade", Objects.toString(c.getGradeLevel(), ""), "coverUrl", Objects.toString(c.getCoverUrl(), ""),
                    "finalPrice", c.getFinalPrice())).toList());
            v.put("teacher", t);
            return v;
        }).toList());
        return out;
    }

    private TeacherAcademy publishedAcademy(TeacherBundleMember m) {
        return m.getAcademyId() == null ? null : academies.findById(m.getAcademyId()).filter(TeacherAcademy::isPublished).orElse(null);
    }
    private static String name(TeacherBundleMember m, TeacherAcademy a) {
        return !m.getDisplayName().isBlank() ? m.getDisplayName() : a != null ? a.getName() : "";
    }
    private static String photo(TeacherBundleMember m, TeacherAcademy a) {
        return !m.getPhotoUrl().isBlank() ? m.getPhotoUrl() : a != null ? Objects.toString(a.getPhotoUrl(), "") : "";
    }

    // ---- Head office -------------------------------------------------------------------------------------------

    public List<Map<String, Object>> list(UserPrincipal actor) {
        requireHeadOffice(actor);
        return bundles.findAllByOrderBySortOrderAscIdAsc().stream()
                .filter(b -> b.getManagerTenantId() == null || b.getManagerTenantId().equals(actor.getTenantId()))
                .map(this::adminView).toList();
    }

    @Transactional
    public Map<String, Object> create(UserPrincipal actor, BundleRequest req) {
        requireHeadOffice(actor);
        var b = new TeacherBundle();
        b.setManagerTenantId(actor.getTenantId());
        apply(actor, b, req);
        audit.record(actor, "BUNDLE_CREATED", "TeacherBundle", b.getId(), null, b.getSlug());
        return adminView(b);
    }

    @Transactional
    public Map<String, Object> update(UserPrincipal actor, Long id, BundleRequest req) {
        var b = manage(actor, id);
        if (b.getManagerTenantId() == null) b.setManagerTenantId(actor.getTenantId());
        apply(actor, b, req);
        audit.record(actor, "BUNDLE_CHANGED", "TeacherBundle", id, null, b.getSlug());
        return adminView(b);
    }

    @Transactional
    public void delete(UserPrincipal actor, Long id) {
        var b = manage(actor, id);
        members.deleteByBundleId(b.getId());
        bundles.delete(b);
        audit.record(actor, "BUNDLE_DELETED", "TeacherBundle", id, null, b.getSlug());
    }

    private TeacherBundle manage(UserPrincipal actor, Long id) {
        requireHeadOffice(actor);
        var b = bundles.findById(id).orElseThrow(() -> NotFoundException.of("الباقة", id));
        if (b.getManagerTenantId() != null && !b.getManagerTenantId().equals(actor.getTenantId()))
            throw new ForbiddenException("هذه الباقة تتبع إدارة أخرى");
        return b;
    }

    /** Packages group teacher spaces, so only the office that manages those spaces handles them — never a teacher. */
    private void requireHeadOffice(UserPrincipal actor) {
        if (!actor.isAdmin() || academies.findByTenantId(actor.getTenantId()).isPresent())
            throw new ForbiddenException("باقات المدرسين تُدار من الإدارة الرئيسية فقط");
    }

    private void apply(UserPrincipal actor, TeacherBundle b, BundleRequest req) {
        String slug = required(req.slug(), 70).toLowerCase(Locale.ROOT);
        if (!SLUG.matcher(slug).matches()) throw new BadRequestException("استخدم حروفاً إنجليزية وأرقاماً وشرطات في رابط الباقة");
        bundles.findBySlug(slug).filter(other -> !other.getId().equals(b.getId()))
                .ifPresent(other -> { throw new ConflictException("رابط الباقة مستخدم بالفعل"); });
        b.setSlug(slug);
        b.setName(required(req.name(), 150));
        b.setTagline(optional(req.tagline(), 220));
        b.setDescription(optional(req.description(), 1500));
        if (req.published() != null) b.setPublished(req.published());
        if (req.sortOrder() != null) b.setSortOrder(req.sortOrder());
        var list = req.members() == null ? List.<MemberRequest>of() : req.members();
        if (list.isEmpty()) throw new BadRequestException("أضف مدرساً واحداً على الأقل للباقة");
        if (list.size() > MAX_MEMBERS) throw new BadRequestException("الباقة تضم " + MAX_MEMBERS + " مدرساً بحد أقصى");
        var validated = new ArrayList<TeacherBundleMember>();
        var linked = new HashSet<Long>();
        for (var r : list) {
            var m = new TeacherBundleMember();
            if (r.academyId() != null) {
                var a = academies.findById(r.academyId()).filter(x -> actor.getTenantId().equals(x.getManagerTenantId()))
                        .orElseThrow(() -> new BadRequestException("اختر مساحات مدرسين تديرها إدارتك فقط"));
                if (!linked.add(a.getId())) throw new BadRequestException("المدرس مضاف مرتين في نفس الباقة");
                m.setAcademyId(a.getId());
            }
            m.setDisplayName(optional(r.displayName(), 120));
            m.setSubject(required(r.subject(), 100));
            String photo = optional(r.photoUrl(), 2000);
            if (!photo.isEmpty() && !PHOTO.matcher(photo).matches()) throw new BadRequestException("رابط صورة المدرس غير صحيح");
            m.setPhotoUrl(photo);
            String video = optional(r.introVideoUrl(), 2000);
            if (!video.isEmpty() && !VIDEO.matcher(video).matches()) throw new BadRequestException("استخدم رابط HTTPS لفيديو التعريف");
            m.setIntroVideoUrl(video);
            validated.add(m);
        }
        bundles.save(b);
        members.deleteByBundleId(b.getId());
        members.flush();
        for (int i = 0; i < validated.size(); i++) {
            var m = validated.get(i); m.setBundleId(b.getId()); m.setPosition(i); members.save(m);
        }
    }

    private Map<String, Object> adminView(TeacherBundle b) {
        Map<String, Object> v = new LinkedHashMap<>();
        v.put("id", b.getId()); v.put("slug", b.getSlug()); v.put("name", b.getName()); v.put("tagline", b.getTagline());
        v.put("description", b.getDescription()); v.put("published", b.isPublished()); v.put("sortOrder", b.getSortOrder());
        v.put("members", members.findByBundleIdOrderByPositionAsc(b.getId()).stream().map(m -> {
            Map<String, Object> mv = new LinkedHashMap<>();
            mv.put("academyId", m.getAcademyId()); mv.put("displayName", m.getDisplayName()); mv.put("subject", m.getSubject());
            mv.put("photoUrl", m.getPhotoUrl()); mv.put("introVideoUrl", m.getIntroVideoUrl());
            return mv;
        }).toList());
        return v;
    }

    private static String required(String s, int max) {
        if (s == null || s.isBlank() || s.trim().length() > max) throw new BadRequestException("أكمل الحقول المطلوبة ضمن الحد المسموح");
        return s.trim();
    }
    private static String optional(String s, int max) {
        String v = s == null ? "" : s.trim();
        if (v.length() > max) throw new BadRequestException("أحد الحقول أطول من المسموح");
        return v;
    }
}
