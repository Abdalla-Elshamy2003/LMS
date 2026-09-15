package com.manarah.security;

import com.manarah.common.exception.ApiExceptions.BadRequestException;

/** One password policy for registration, staff-created accounts, resets and password changes. */
public final class PasswordPolicy {
    private PasswordPolicy() {}

    public static String requireStrong(String password) {
        if (password == null || password.length() < 10 || password.length() > 72
                || password.chars().noneMatch(Character::isLetter)
                || password.chars().noneMatch(Character::isDigit)) {
            throw new BadRequestException("كلمة المرور يجب أن تكون من 10 إلى 72 حرفاً وتحتوي على حروف وأرقام");
        }
        return password;
    }
}
