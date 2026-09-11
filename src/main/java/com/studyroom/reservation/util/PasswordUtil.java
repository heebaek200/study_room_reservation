package com.studyroom.reservation.util;

import com.studyroom.reservation.exception.BusinessException;
import org.mindrot.jbcrypt.BCrypt;

public final class PasswordUtil {

    private PasswordUtil() {
    }

    public static String hash(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new BusinessException("비밀번호를 입력해 주세요.");
        }

        return BCrypt.hashpw(rawPassword, BCrypt.gensalt());
    }

    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || passwordHash == null) {
            return false;
        }

        return BCrypt.checkpw(rawPassword, passwordHash);
    }
}
