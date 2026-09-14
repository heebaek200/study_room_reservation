package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.Reservation;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;

/**
 * 회원 예약 생성 DAO
 */
public class ReservationCreateDAO {

    /**
     * 예약 대상 스터디룸을 잠그고(FOR UPDATE) 시간당 이용료를 조회합니다.
     * 존재하지 않는 스터디룸이면 null을 반환합니다.
     */
    public BigDecimal findHourlyRateForUpdate(Connection connection, long roomId) throws SQLException {
        String sql = """
                SELECT
                    hourly_rate
                FROM study_room
                WHERE
                    room_id = ?
                FOR UPDATE;
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }

                return rs.getBigDecimal("hourly_rate");
            }
        }
    }

    /**
     * 같은 방에 겹치는 CONFIRMED 예약이 있는지 확인합니다.
     */
    public boolean existsOverlappingConfirmed(Connection connection, long roomId, LocalDateTime startTime, LocalDateTime endTime)
            throws SQLException {
        String sql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM reservation
                    WHERE room_id = ?
                      AND status = 'CONFIRMED'
                      AND start_time < ?
                      AND end_time > ?
                ) AS exists_overlap;
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, roomId);
            pstmt.setTimestamp(2, Timestamp.valueOf(endTime));
            pstmt.setTimestamp(3, Timestamp.valueOf(startTime));

            try (ResultSet rs = pstmt.executeQuery()) {
                rs.next();
                return rs.getBoolean("exists_overlap");
            }
        }
    }

    /**
     * 검증과 금액 계산이 완료된 예약을 저장합니다.
     */
    public int insert(Connection connection, Reservation reservation) throws SQLException {
        String sql = """
                INSERT INTO reservation (
                    user_id
                  , room_id
                  , start_time
                  , end_time
                  , total_price
                  , status
                ) VALUES (
                    ?
                  , ?
                  , ?
                  , ?
                  , ?
                  , ?
                );
                """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, reservation.getUserId());      // user_id
            pstmt.setLong(2, reservation.getRoomId());      // room_id
            pstmt.setTimestamp(                                             // start_time
                    3,
                    Timestamp.valueOf(reservation.getStartTime())
            );
            pstmt.setTimestamp(                                             // end_time
                    4,
                    Timestamp.valueOf(reservation.getEndTime())
            );
            pstmt.setBigDecimal(                                            // total_price
                    5,
                    reservation.getTotalPrice()
            );
            pstmt.setString(                                                // status
                    6,
                    reservation.getStatus().name()
            );

            return pstmt.executeUpdate();
        }
    }
}