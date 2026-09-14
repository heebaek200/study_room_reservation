package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Optional;

/**
 * 회원가입과 로그인에 필요한 users 테이블 접근을 담당합니다.
 * 회원 정보 수정·탈퇴·관리자 회원 조회는 UserDAO에서 처리합니다.
 * 비밀번호 해시 생성과 비교는 Service 또는 PasswordUtil에서 처리합니다.
 *
 * 메서드	            역할
 * existsByEmail()	    탈퇴 회원을 포함한 이메일 중복 확인
 * insert()	            해시된 비밀번호와 신규 회원 기본값 저장
 * findActiveByEmail()	로그인 가능한 회원 조회
 */
public class AuthDAO {

    /**
     * 전달받은 이메일이 users 테이블에 이미 존재하는지 확인합니다.
     * ACTIVE와 WITHDRAWN 상태를 구분하지 않고 모든 회원을 검색합니다.
     * 탈퇴한 회원의 이메일도 다시 사용할 수 없도록 중복으로 판단합니다.
     *
     * @param email 중복 여부를 확인할 이메일
     * @return 이메일이 이미 존재하면 true, 존재하지 않으면 false
     */
    public boolean existsByEmail(String email) {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM users
                    WHERE email = ?
                ) AS is_exists
                """;

        // PreparedStatement를 사용하여 입력값을 SQL과 분리합니다.
        try (
                Connection connection = DatabaseUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, email);

            // EXISTS의 조회 결과는 반드시 한 행이 반환됩니다.
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return resultSet.getBoolean("is_exists");
                }
            }
        } catch (SQLException e) {
            // 이메일 등의 입력값은 예외 메시지에 포함하지 않습니다.
            throw new IllegalStateException("이메일 중복 확인 중 오류가 발생했습니다.", e);
        }

        return false;
    }

    /**
     * 회원가입 입력값을 users 테이블에 저장합니다.
     * passwordHash에는 평문이 아닌 BCrypt 해시값이 전달되어야 합니다.
     * 신규 회원의 기본 권한과 상태는 USER, ACTIVE로 저장합니다.
     *
     * @param user 회원가입 정보를 담은 User 객체
     * @return 정상적으로 추가된 행의 개수
     */
    public int insert(User user) {
        String sql = """
                INSERT INTO users (
                    email
                  , password_hash
                  , name
                  , role
                  , status
                ) VALUES (
                    ?
                  , ?
                  , ?
                  , ?
                  , ?
                )
                """;

        // AuthDAO는 전달받은 해시값을 저장하며 직접 암호화하지 않습니다.
        try (
                Connection connection = DatabaseUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, user.getEmail());
            statement.setString(2, user.getPasswordHash());
            statement.setString(3, user.getName());
            statement.setString(4, UserRole.USER.name());
            statement.setString(5, UserStatus.ACTIVE.name());

            // 호출한 Service에서 성공 여부를 판단할 수 있도록 행 개수를 반환합니다.
            return statement.executeUpdate();
        } catch (SQLException e) {
            // 비밀번호 해시나 이메일은 예외 메시지에 출력하지 않습니다.
            throw new IllegalStateException("회원가입 처리 중 오류가 발생했습니다.", e);
        }
    }

    /**
     * 로그인에 사용할 ACTIVE 회원을 이메일로 조회합니다.
     * 존재하지 않거나 WITHDRAWN 상태인 회원은 Optional.empty()를 반환합니다.
     * 비밀번호 일치 여부는 이 메서드가 아닌 Service에서 검사합니다.
     *
     * @param email 로그인 시 입력한 이메일
     * @return 조회된 ACTIVE 회원 또는 Optional.empty()
     */
    public Optional<User> findActiveByEmail(String email) {
        String sql = """
                SELECT
                    user_id
                  , email
                  , password_hash
                  , name
                  , role
                  , status
                FROM users
                WHERE email = ?
                  AND status = ?
                """;

        // 탈퇴 회원은 조회 단계에서 제외하여 로그인할 수 없게 합니다.
        try (
                Connection connection = DatabaseUtil.getConnection();
                PreparedStatement statement = connection.prepareStatement(sql)
        ) {
            statement.setString(1, email);
            statement.setString(2, UserStatus.ACTIVE.name());

            // 이메일에는 UNIQUE 제약조건이 있으므로 최대 한 행만 조회됩니다.
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        } catch (SQLException e) {
            // 로그인 입력값은 로그나 예외 메시지에 포함하지 않습니다.
            throw new IllegalStateException("로그인 회원 조회 중 오류가 발생했습니다.", e);
        }

        return Optional.empty();
    }

    /**
     * users 조회 결과 한 행을 User DTO로 변환합니다.
     * 데이터베이스의 문자열 상태값을 해당 Enum으로 변환합니다.
     * 조회와 객체 생성 로직을 분리하여 DAO 메서드의 중복을 줄입니다.
     *
     * @param resultSet 현재 회원 행을 가리키는 ResultSet
     * @return 조회 결과를 담은 User 객체
     * @throws SQLException 컬럼 값을 읽지 못한 경우
     */
    private User mapUser(ResultSet resultSet) throws SQLException {
        // 로그인 후 세션 생성에 필요한 회원 ID와 권한도 함께 담습니다.
        return User.builder()
                .userId(resultSet.getLong("user_id"))
                .email(resultSet.getString("email"))
                .passwordHash(resultSet.getString("password_hash"))
                .name(resultSet.getString("name"))
                .role(UserRole.valueOf(resultSet.getString("role")))
                .status(UserStatus.valueOf(resultSet.getString("status")))
                .build();
    }
}