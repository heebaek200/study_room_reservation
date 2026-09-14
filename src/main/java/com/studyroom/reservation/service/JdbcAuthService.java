package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.AuthDAO;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.PasswordUtil;
import com.studyroom.reservation.session.SessionManager;

import java.util.Locale;

/**
 * AuthService의 정식 JDBC 구현체입니다.
 * 회원 데이터는 AuthDAO를 통해 MySQL의 users 테이블에 저장하고 조회합니다.
 * 기존 InMemoryAuthService는 다른 Issue의 병렬 개발을 위해 그대로 유지합니다.
 */
public final class JdbcAuthService implements AuthService {

    private static final String LOGIN_FAILURE_MESSAGE =
            "이메일 또는 비밀번호가 올바르지 않습니다.";

    private static final int MIN_PASSWORD_LENGTH = 6;

    private final AuthDAO authDAO;
    private final SessionManager sessionManager;

    /**
     * 인증 처리에 사용할 AuthDAO와 SessionManager를 생성합니다.
     * 회원 정보는 AuthDAO를 통해 조회하고 로그인 세션은 메모리에서 관리합니다.
     * UserDAO와 UserService에는 의존하지 않습니다.
     */
    public JdbcAuthService() {
        this.authDAO = new AuthDAO();
        this.sessionManager = new SessionManager();
    }

    /**
     * 회원가입 입력값을 검증하고 신규 회원을 데이터베이스에 저장합니다.
     * 이메일은 앞뒤 공백을 제거하고 소문자로 통일합니다.
     * 비밀번호는 BCrypt 해시로 변환한 뒤 AuthDAO에 전달합니다.
     *
     * @param email       회원가입에 사용할 이메일
     * @param rawPassword 사용자가 입력한 평문 비밀번호
     * @param name        회원 이름
     */
    @Override
    public void signUp(String email, String rawPassword, String name) {
        String normalizedEmail = normalizeEmail(email);
        String normalizedName = normalizeName(name);

        requireNotBlank(rawPassword, "비밀번호");

        // HTML 검증을 우회한 요청도 가입할 수 없도록 서버에서 다시 검사합니다.
        if (rawPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new BusinessException(
                    "비밀번호는 " + MIN_PASSWORD_LENGTH + "자 이상이어야 합니다."
            );
        }

        // ACTIVE와 WITHDRAWN을 구분하지 않고 기존 이메일을 모두 검사합니다.
        if (authDAO.existsByEmail(normalizedEmail)) {
            throw new BusinessException("이미 사용 중인 이메일입니다.");
        }

        // 평문 비밀번호는 User DTO나 데이터베이스에 저장하지 않습니다.
        String passwordHash = PasswordUtil.hash(rawPassword);

        User user = User.builder()
                .email(normalizedEmail)
                .passwordHash(passwordHash)
                .name(normalizedName)
                .role(UserRole.USER)
                .status(UserStatus.ACTIVE)
                .build();

        int affectedRows = authDAO.insert(user);

        // 회원 INSERT는 정확히 한 행이 반영되어야 합니다.
        if (affectedRows != 1) {
            throw new BusinessException("회원가입 처리에 실패했습니다.");
        }
    }

    /**
     * 이메일과 비밀번호를 검증하고 로그인 세션을 생성합니다.
     * ACTIVE 상태의 회원만 로그인할 수 있으며 WITHDRAWN 회원은 차단합니다.
     * 존재하지 않는 이메일과 비밀번호 불일치는 같은 실패 메시지로 처리합니다.
     *
     * @param email       로그인 이메일
     * @param rawPassword 사용자가 입력한 평문 비밀번호
     * @return 로그인 회원의 ID와 권한을 담은 LoginSession
     * @throws BusinessException 로그인 정보가 올바르지 않은 경우
     */
    @Override
    public LoginSession login(String email, String rawPassword) {
        // 로그인 실패 사유가 외부에 구체적으로 노출되지 않도록 통일합니다.
        if (email == null
                || email.isBlank()
                || rawPassword == null
                || rawPassword.isBlank()) {
            throw new BusinessException(LOGIN_FAILURE_MESSAGE);
        }

        String normalizedEmail = email
                .strip()
                .toLowerCase(Locale.ROOT);

        // AuthDAO는 ACTIVE 상태의 회원만 반환합니다.
        User user = authDAO.findActiveByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(LOGIN_FAILURE_MESSAGE));

        // 평문 비밀번호와 데이터베이스의 BCrypt 해시값을 비교합니다.
        if (!PasswordUtil.matches(rawPassword, user.getPasswordHash())) {
            throw new BusinessException(LOGIN_FAILURE_MESSAGE);
        }

        // 세션에는 비밀번호와 이메일을 제외하고 최소 인증 정보만 저장합니다.
        return sessionManager.createSession(
                user.getUserId(),
                user.getRole()
        );
    }

    /**
     * 전달받은 세션 ID에 해당하는 로그인 세션을 제거합니다.
     * 존재하지 않거나 비어 있는 세션 ID가 전달되어도 오류를 발생시키지 않습니다.
     * 세션이 제거된 이후에는 보호된 기능에 접근할 수 없습니다.
     *
     * @param sessionId 제거할 로그인 세션 ID
     */
    @Override
    public void logout(String sessionId) {
        // 실제 세션 삭제는 기존 SessionManager에 위임합니다.
        sessionManager.removeSession(sessionId);
    }

    /**
     * 전달받은 세션 ID가 현재 유효한 로그인 세션인지 확인합니다.
     * 유효한 세션이면 회원 ID와 권한이 담긴 LoginSession을 반환합니다.
     * 세션이 없거나 만료되었다면 SessionManager에서 BusinessException을 발생시킵니다.
     *
     * @param sessionId 확인할 로그인 세션 ID
     * @return 현재 로그인한 회원의 세션
     * @throws BusinessException 유효한 로그인 세션이 없는 경우
     */
    @Override
    public LoginSession requireLogin(String sessionId) {
        // 세션 조회와 로그인 실패 처리는 SessionManager에 위임합니다.
        return sessionManager.requireSession(sessionId);
    }

    /**
     * 이메일을 저장과 비교에 사용할 동일한 형식으로 정규화합니다.
     * 앞뒤 공백을 제거하고 Locale과 무관하게 소문자로 변환합니다.
     * 입력값이 없으면 BusinessException을 발생시킵니다.
     *
     * @param email 사용자가 입력한 이메일
     * @return 정규화된 이메일
     */
    private String normalizeEmail(String email) {
        requireNotBlank(email, "이메일");

        // 시스템의 지역 설정과 무관하게 같은 변환 결과를 사용합니다.
        return email.strip().toLowerCase(Locale.ROOT);
    }

    /**
     * 회원 이름의 앞뒤 공백을 제거합니다.
     * 이름 내부의 공백은 사용자가 입력한 그대로 유지합니다.
     * 입력값이 없으면 BusinessException을 발생시킵니다.
     *
     * @param name 사용자가 입력한 회원 이름
     * @return 앞뒤 공백을 제거한 이름
     */
    private String normalizeName(String name) {
        requireNotBlank(name, "이름");
        return name.strip();
    }

    /**
     * 필수 입력값이 null 또는 공백인지 확인합니다.
     * 검증에 실패하면 기존 인증 구현체와 동일하게 BusinessException을 사용합니다.
     * 실제 입력값은 예외 메시지에 포함하지 않습니다.
     *
     * @param value     검사할 입력값
     * @param fieldName 사용자에게 표시할 항목명
     */
    private void requireNotBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(fieldName + "을(를) 입력해 주세요.");
        }
    }
}
