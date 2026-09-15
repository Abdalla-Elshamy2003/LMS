package com.manarah.academy;
import com.manarah.identity.repo.UserRepository;
import com.manarah.identity.domain.Role;
import com.manarah.security.UserPrincipal;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.util.UUID;

/** Additive starter page; the administrator sets credentials before the teacher can log in. */
@Component
public class AcademyBootstrap {
    private final AcademyService service;
    private final TeacherAcademyRepository academies;
    private final UserRepository users;
    public AcademyBootstrap(AcademyService service, TeacherAcademyRepository academies, UserRepository users) {
        this.service = service; this.academies = academies; this.users = users;
    }
    @EventListener(ApplicationReadyEvent.class) @Transactional
    public void initialize() {
        if (academies.count() != 0) return;
        users.findAll().stream().filter(u -> u.getRole() == Role.SUPER_ADMIN).findFirst().ifPresent(admin -> {
            var a = service.create(UserPrincipal.from(admin), new AcademyService.CreateAcademy("محمد سليمان", "mohamed-soliman", "mohamed.soliman", UUID.randomUUID().toString()));
            a.setPhotoUrl("/images/mohamed-soliman.png"); a.setDemoContent(true); academies.save(a);
            var teacher = users.findById(a.getTeacherId()).orElseThrow(); teacher.setStatus("INACTIVE"); users.save(teacher);
        });
    }
}
