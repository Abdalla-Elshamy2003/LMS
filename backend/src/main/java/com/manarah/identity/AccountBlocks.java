package com.manarah.identity;

import com.manarah.academy.TeacherAcademy;
import com.manarah.academy.TeacherAcademyRepository;
import com.manarah.identity.domain.Role;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.student.domain.Student;
import com.manarah.student.repo.StudentRepository;
import org.springframework.stereotype.Component;

/**
 * Whether someone is blocked right now, and the message that tells them why. Three kinds of block:
 * <ul>
 *   <li>head office blocks a teacher or a student on the whole platform — the account they sign in with
 *       ({@code users.blocked_at});</li>
 *   <li>a blocked teacher's assistants are shut out of that teacher's space with them;</li>
 *   <li>a teacher blocks one of their students in their own space only — that seat ({@code students.blocked_at}); the
 *       student's other teachers are unaffected.</li>
 * </ul>
 * Sign-in and every request ask here (see {@code AuthService} and {@code JwtAuthFilter}); the reason is only ever
 * told after the password has been checked.
 */
@Component
public class AccountBlocks {
    private final TeacherAcademyRepository academies;
    private final UserRepository users;
    private final StudentRepository students;

    public AccountBlocks(TeacherAcademyRepository academies, UserRepository users, StudentRepository students) {
        this.academies = academies; this.users = users; this.students = students;
    }

    /** Why the account itself may not be used anywhere (a platform block, or its teacher's space is blocked), or null. */
    public String accountReason(User account) {
        if (account == null) return null;
        if (account.getBlockedAt() != null) return platform(account.getBlockedReason());
        if (account.getRole() == Role.ASSISTANT) {
            TeacherAcademy academy = academies.findByTenantId(account.getTenantId()).orElse(null);
            User teacher = academy == null || academy.getTeacherId() == null ? null : users.findById(academy.getTeacherId()).orElse(null);
            if (teacher != null && teacher.getBlockedAt() != null) return space(academy.getName(), teacher.getBlockedReason());
        }
        return null;
    }

    /**
     * Why {@code current} — the row the session is for, signed in through {@code owner} (the same row, or a student's
     * main account) — may not be used right now, or null.
     */
    public String reason(User current, User owner) {
        String account = accountReason(owner);
        if (account != null) return account;
        if (current == null) return null;
        if (owner == null || !current.getId().equals(owner.getId())) {
            account = accountReason(current);
            if (account != null) return account;
        }
        if (current.getRole() == Role.STUDENT)
            return students.findByTenantIdAndUserId(current.getTenantId(), current.getId()).map(this::seatReason).orElse(null);
        return null;
    }

    /** Why this seat is closed by its teacher, or null. */
    public String seatReason(Student seat) {
        if (seat == null || seat.getBlockedAt() == null) return null;
        String teacher = academies.findByTenantId(seat.getTenantId()).map(TeacherAcademy::getName).orElse("المدرس");
        return teacherBlock(teacher, seat.getBlockedReason());
    }

    public static String platform(String reason) {
        return "حسابك موقوف من إدارة المنصة.\nالسبب: " + reason + "\nلو شايف إن ده غلط، تواصل مع إدارة المنصة.";
    }

    public static String space(String teacher, String reason) {
        return "مساحة " + teacher + " موقوفة من إدارة المنصة، فالدخول عليها متوقف دلوقتي.\nالسبب: " + reason;
    }

    public static String teacherBlock(String teacher, String reason) {
        return teacher + " وقّف حسابك عنده.\nالسبب: " + reason + "\nلو شايف إن ده غلط، تواصل معاه.";
    }
}
