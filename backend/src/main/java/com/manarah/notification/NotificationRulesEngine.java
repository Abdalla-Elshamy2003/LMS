package com.manarah.notification;

import com.manarah.course.repo.CourseRepository;
import com.manarah.enrollment.repo.EnrollmentRepository;
import com.manarah.notification.domain.NotificationRule;
import com.manarah.notification.repo.NotificationRuleRepository;
import com.manarah.student.domain.Guardian;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.GuardianRepository;
import com.manarah.student.repo.StudentGuardianRepository;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The rules engine (§34): given a domain trigger, finds the tenant's active rules, evaluates the
 * threshold, resolves recipients (parent / teacher) and raises notifications on the rule's channels.
 * Called by the domain-event listeners and by risk detection — never by core services directly.
 *
 * <p>Each rule may carry a custom {@code template} with {@code {{placeholder}}} tokens (e.g.
 * {@code "يا {{name}}، احنا مفتقدينك في {{course}} النهاردة!"}) — set from the Rules admin page.
 * A blank template falls back to the built-in default wording for that trigger.
 */
@Component
public class NotificationRulesEngine {

    private final NotificationRuleRepository rules;
    private final NotificationService notifications;
    private final StudentRepository students;
    private final StudentGuardianRepository studentGuardians;
    private final GuardianRepository guardians;
    private final EnrollmentRepository enrollments;
    private final CourseRepository courses;

    public NotificationRulesEngine(NotificationRuleRepository rules, NotificationService notifications,
                                   StudentRepository students, StudentGuardianRepository studentGuardians,
                                   GuardianRepository guardians, EnrollmentRepository enrollments,
                                   CourseRepository courses) {
        this.rules = rules;
        this.notifications = notifications;
        this.students = students;
        this.studentGuardians = studentGuardians;
        this.guardians = guardians;
        this.enrollments = enrollments;
        this.courses = courses;
    }

    public void onStudentAbsent(Long tenantId, Long studentId, String courseTitle) {
        fire(tenantId, studentId, "STUDENT_ABSENT", null, "تغيّب الطالب",
                Map.of("course", courseTitle),
                name -> "الطالب " + name + " لم يحضر محاضرة " + courseTitle + " اليوم.");
    }

    public void onLowScore(Long tenantId, Long studentId, String title, double percent) {
        fire(tenantId, studentId, "LOW_SCORE", percent, "درجة منخفضة",
                Map.of("title", title, "percent", fmt(percent)),
                name -> "حصل الطالب " + name + " على " + fmt(percent) + "% في " + title + ".");
    }

    public void onHomeworkMissed(Long tenantId, Long studentId, String title, int count) {
        fire(tenantId, studentId, "HOMEWORK_MISSED", (double) count, "واجب غير مُسلَّم",
                Map.of("title", title, "count", String.valueOf(count)),
                name -> "الطالب " + name + " لم يسلّم الواجب" + (count > 1 ? " للمرة " + count : "") + ": " + title + ".");
    }

    public void onRiskEscalated(Long tenantId, Long studentId, String level, List<String> reasons) {
        String reasonsText = String.join("، ", reasons);
        fire(tenantId, studentId, "RISK_ESCALATED", null, "تنبيه: تراجع مستوى الطالب",
                Map.of("level", level, "reasons", reasonsText),
                name -> "الطالب " + name + " أصبح معرّضاً للتراجع الدراسي (" + level + ")."
                        + (reasons.isEmpty() ? "" : " الأسباب: " + reasonsText));
    }

    public void onInstallmentDue(Long tenantId, Long studentId, String amount, String dueDate) {
        fire(tenantId, studentId, "INSTALLMENT_DUE", null, "تذكير بقسط مستحق",
                Map.of("amount", amount, "dueDate", dueDate),
                name -> "تذكير: القسط القادم للطالب " + name + " بقيمة " + amount + " جنيه مستحق يوم " + dueDate + ".");
    }

    // ---- core ----

    private interface Message {
        String render(String studentName);
    }

    private void fire(Long tenantId, Long studentId, String trigger, Double value, String category,
                      Map<String, String> placeholders, Message defaultMessage) {
        List<NotificationRule> active = rules.findByTenantIdAndTriggerTypeAndActiveTrue(tenantId, trigger);
        if (active.isEmpty()) {
            return;
        }
        Student student = students.findByTenantIdAndId(tenantId, studentId).orElse(null);
        if (student == null) {
            return;
        }
        String defaultBody = defaultMessage.render(student.getFullName());
        Map<String, String> vars = new LinkedHashMap<>(placeholders);
        vars.put("name", student.getFullName());
        for (NotificationRule rule : active) {
            if (rule.getThreshold() != null && value != null) {
                // LOW_SCORE fires when value <= threshold; count-based fires when value >= threshold.
                boolean pass = "LOW_SCORE".equals(trigger) ? value <= rule.getThreshold() : value >= rule.getThreshold();
                if (!pass) {
                    continue;
                }
            }
            String body = (rule.getTemplate() != null && !rule.getTemplate().isBlank())
                    ? applyTemplate(rule.getTemplate(), vars) : defaultBody;
            List<String> channels = Arrays.stream(rule.getChannels().split(",")).map(String::trim).filter(c -> !c.isEmpty()).toList();
            if (rule.isNotifyParent()) {
                for (Guardian g : resolveGuardians(tenantId, studentId)) {
                    // No userId check here on purpose: a guardian added at registration has a phone
                    // and no account, and skipping them meant the parents these alerts are for
                    // received nothing at all. notify() dispatches WhatsApp/SMS either way and only
                    // files an in-app copy when there is an account to file it against.
                    if (g.getUserId() == null && (g.getPhone() == null || g.getPhone().isBlank())) {
                        continue;
                    }
                    notifications.notify(tenantId, new NotificationDtos.NotifyCommand(
                            g.getUserId(), g.getPhone(), category, body, category, "STUDENT", studentId, channels));
                }
            }
            if (rule.isNotifyTeacher()) {
                for (Long teacherUserId : resolveTeachers(tenantId, studentId)) {
                    notifications.notify(tenantId, new NotificationDtos.NotifyCommand(
                            teacherUserId, null, category, body, category, "STUDENT", studentId, channels));
                }
            }
        }
    }

    /** Replaces {@code {{key}}} tokens; a token with no matching value is left as-is rather than
     *  silently dropped, so an admin typo in the template is visible instead of hidden. */
    private static String applyTemplate(String template, Map<String, String> vars) {
        String result = template;
        for (var e : vars.entrySet()) {
            result = result.replace("{{" + e.getKey() + "}}", e.getValue() == null ? "" : e.getValue());
        }
        return result;
    }

    private List<Guardian> resolveGuardians(Long tenantId, Long studentId) {
        List<Guardian> result = new ArrayList<>();
        for (var link : studentGuardians.findByTenantIdAndStudentId(tenantId, studentId)) {
            guardians.findByTenantIdAndId(tenantId, link.getGuardianId()).ifPresent(result::add);
        }
        return result;
    }

    private Set<Long> resolveTeachers(Long tenantId, Long studentId) {
        Set<Long> teacherIds = new LinkedHashSet<>();
        for (var enr : enrollments.findByTenantIdAndStudentId(tenantId, studentId)) {
            courses.findByTenantIdAndId(tenantId, enr.getCourseId())
                    .filter(c -> c.getTeacherId() != null)
                    .ifPresent(c -> teacherIds.add(c.getTeacherId()));
        }
        return teacherIds;
    }

    private static String fmt(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(Math.round(d * 10) / 10.0);
    }
}
