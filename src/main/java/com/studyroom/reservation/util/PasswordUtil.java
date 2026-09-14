package com.studyroom.reservation.util;

import org.mindrot.jbcrypt.BCrypt;

/**
 * 회원 비밀번호의 단방향 해시 생성과 일치 여부 검사를 담당합니다.
 * 평문 비밀번호를 데이터베이스에 저장하지 않도록 BCrypt를 사용합니다.
 * 모든 기능은 정적 메서드로 제공하며 객체로 생성하지 않습니다.
 */
public final class PasswordUtil {

    // 기본값보다 명시적인 설정을 사용하도록 BCrypt 작업 비용을 지정합니다.
    private static final int LOG_ROUNDS = 10;

    // 유틸리티 클래스가 객체로 생성되는 것을 방지합니다.
    private PasswordUtil() {
    }

    /**
     * 전달받은 평문 비밀번호를 BCrypt 해시값으로 변환합니다.
     * 호출할 때마다 새로운 salt가 생성되므로 같은 비밀번호도 다른 결과가 나옵니다.
     * 반환된 해시값만 User DTO와 데이터베이스에 저장해야 합니다.
     *
     * @param rawPassword 사용자가 입력한 평문 비밀번호
     * @return salt 정보가 포함된 BCrypt 해시값
     * @throws IllegalArgumentException 비밀번호가 null이거나 비어 있는 경우
     */
    public static String hash(String rawPassword) {
        validateRawPassword(rawPassword);

        // BCrypt가 생성한 salt를 이용하여 복호화할 수 없는 해시값을 만듭니다.
        return BCrypt.hashpw(
                rawPassword,
                BCrypt.gensalt(LOG_ROUNDS)
        );
    }

    /**
     * 사용자가 입력한 평문 비밀번호와 저장된 BCrypt 해시값을 비교합니다.
     * 평문 비밀번호를 다시 해시하여 문자열끼리 직접 비교해서는 안 됩니다.
     * 저장된 해시값이 없거나 잘못된 형식이면 로그인 실패로 처리합니다.
     *
     * @param rawPassword  사용자가 입력한 평문 비밀번호
     * @param passwordHash 데이터베이스에 저장된 BCrypt 해시값
     * @return 비밀번호가 일치하면 true, 그렇지 않으면 false
     */
    public static boolean matches(String rawPassword, String passwordHash) {
        if (rawPassword == null || rawPassword.isBlank()) {
            return false;
        }

        // 조회 결과에 비밀번호 해시가 없다면 비교하지 않고 실패 처리합니다.
        if (passwordHash == null || passwordHash.isBlank()) {
            return false;
        }

        try {
            // BCrypt가 해시값에 포함된 salt와 작업 비용을 이용하여 비교합니다.
            return BCrypt.checkpw(rawPassword, passwordHash);
        } catch (IllegalArgumentException e) {
            // BCrypt 형식이 아닌 잘못된 해시값도 로그인 실패로 처리합니다.
            return false;
        }
    }

    /**
     * 해시 처리 전에 평문 비밀번호가 입력되었는지 확인합니다.
     * 비밀번호의 길이와 조합 같은 회원가입 규칙은 Service에서 검사합니다.
     * 실제 비밀번호 값은 예외 메시지에 포함하지 않습니다.
     *
     * @param rawPassword 검사할 평문 비밀번호
     * @throws IllegalArgumentException 비밀번호가 null이거나 비어 있는 경우
     */
    private static void validateRawPassword(String rawPassword) {
        if (rawPassword == null || rawPassword.isBlank()) {
            throw new IllegalArgumentException("비밀번호를 입력해야 합니다.");
        }
    }
}