package com.studyroom.reservation.util;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP 커넥션 풀의 생성과 종료를 관리하는 JDBC 유틸리티입니다.
 * 애플리케이션 전체에서 하나의 DataSource를 공유하며, 호출할 때마다 풀에서 Connection을 빌려줍니다.
 * 반환받은 Connection은 사용을 마친 코드에서 반드시 닫아 커넥션 풀에 반납해야 합니다.
 */
public final class DatabaseUtil {

    private static final String URL = "jdbc:mysql://%s:3306/study_room_reservation?serverTimezone=Asia/Seoul";
    private static final String DB_HOST_ENV = "DB_HOST_STUDY_ROOM_RESERVATION";
    private static final String DB_USER_ENV = "DB_USER_STUDY_ROOM_RESERVATION";
    private static final String DB_PASSWORD_ENV = "DB_PASSWORD_STUDY_ROOM_RESERVATION";

    private static final HikariDataSource DATA_SOURCE;

    // 클래스가 처음 사용될 때 애플리케이션 공용 커넥션 풀을 한 번만 생성합니다.
    static {
        HikariConfig config = new HikariConfig();

        // 데이터베이스 접속 정보는 소스 코드에 비밀번호를 남기지 않도록 환경변수에서 읽습니다.
        String host = requireEnvironmentVariable(DB_HOST_ENV).strip();
        String username = requireEnvironmentVariable(DB_USER_ENV).strip();
        String jdbcUrl = String.format(URL, host);

        config.setJdbcUrl(jdbcUrl);
        config.setUsername(username);
        config.setPassword(requireEnvironmentVariable(DB_PASSWORD_ENV));

        // 소규모 팀 프로젝트를 기준으로 최대 연결 수와 최소 대기 연결 수를 설정합니다.
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);

        // 풀이 가득 찬 경우 Connection을 기다릴 최대 시간을 3초로 제한합니다.
        config.setConnectionTimeout(3_000);
        config.setPoolName("StudyRoomReservationPool");

        // 설정을 사용하여 애플리케이션에서 공유할 DataSource를 생성합니다.
        DATA_SOURCE = new HikariDataSource(config);
    }

    // 인스턴스 생성을 막고 모든 기능을 정적 메서드로만 제공합니다.
    private DatabaseUtil() {
    }

    /**
     * HikariCP 커넥션 풀에서 사용 가능한 Connection을 하나 빌려 반환합니다.
     * 반환된 Connection의 Commit, Rollback과 close 처리는 호출한 DAO 또는 Service가 담당합니다.
     * close를 호출하면 실제 연결을 종료하지 않고 커넥션 풀에 반납합니다.
     *
     * @return 커넥션 풀에서 빌린 데이터베이스 Connection
     * @throws SQLException 커넥션을 가져오지 못한 경우
     */
    public static Connection getConnection() throws SQLException {
        return DATA_SOURCE.getConnection();
    }

    /**
     * 애플리케이션 종료 시 HikariCP 커넥션 풀과 내부 Connection을 정리합니다.
     * 일반적인 SQL 작업이 끝날 때마다 호출하지 않고 프로그램이 완전히 종료될 때 한 번만 호출합니다.
     * 이미 종료된 경우에는 중복으로 종료하지 않습니다.
     */
    public static void close() {
        if (!DATA_SOURCE.isClosed()) {
            DATA_SOURCE.close();
        }
    }

    /**
     * 이름으로 지정한 환경변수를 읽고 필수값이 설정되어 있는지 확인합니다.
     * 환경변수가 없거나 공백이면 데이터베이스 연결을 시도하기 전에 명확한 예외를 발생시킵니다.
     * 비밀번호 등의 실제 환경변수 값은 예외 메시지에 포함하지 않습니다.
     *
     * @param name 읽을 환경변수 이름
     * @return 공백이 아닌 환경변수 값
     * @throws IllegalStateException 필수 환경변수가 설정되지 않은 경우
     */
    private static String requireEnvironmentVariable(String name) {
        String value = System.getenv(name);

        // 값 자체는 노출하지 않고 누락된 환경변수의 이름만 안내합니다.
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("필수 환경변수가 설정되지 않았습니다: " + name);
        }

        return value;
    }
}
