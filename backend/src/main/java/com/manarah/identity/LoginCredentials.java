package com.manarah.identity;

import com.manarah.common.exception.ApiExceptions.BadRequestException;
import com.manarah.common.exception.ApiExceptions.ConflictException;
import com.manarah.identity.domain.User;
import com.manarah.identity.repo.UserRepository;
import com.manarah.security.PasswordPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Objects;

/**
 * A username sign-in (teacher spaces, centers, admins): the name rules, platform-wide uniqueness — login looks a
 * username up across every tenant — and the password policy, in one place.
 */
@Component
public class LoginCredentials {
    private final UserRepository users;
    private final PasswordEncoder passwords;

    public LoginCredentials(UserRepository users, PasswordEncoder passwords) {
        this.users = users; this.passwords = passwords;
    }

    /** Sets the (lower-cased) username and a strong password on {@code u}, and marks it ACTIVE. */
    public void assign(User u, String username, String password) {
        String name = username(u, username);
        u.setUsername(name);
        u.setPasswordHash(passwords.encode(PasswordPolicy.requireStrong(password)));
        u.setStatus("ACTIVE");
    }

    /** The cleaned username, refused when it breaks the rules or another account already signs in with it. */
    public String username(User u, String username) {
        if (username == null || username.isBlank() || username.trim().length() > 50)
            throw new BadRequestException("أكمل الحقول المطلوبة ضمن الحد المسموح");
        String name = username.trim().toLowerCase(Locale.ROOT);
        if (!name.matches("[a-z0-9][a-z0-9._-]{2,49}")) throw new BadRequestException("اسم المستخدم من 3 إلى 50 حرفاً إنجليزياً أو رقماً أو . أو _ أو -");
        var existing = users.findByUsernameIgnoreCase(name);
        if (existing.isPresent() && !Objects.equals(existing.get().getId(), u.getId())) throw new ConflictException("اسم المستخدم مستخدم بالفعل");
        return name;
    }
}
