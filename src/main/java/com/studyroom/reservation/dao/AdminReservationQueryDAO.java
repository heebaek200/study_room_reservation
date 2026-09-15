package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.enums.ReservationStatus;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 관리자의 전체 예약 목록·상세 조회를 담당합니다.
 * 관리자 권한 검사는 이 DAO를 호출하는 Service에서 처리합니다.
 */
public class AdminReservationQueryDAO {

    /**
     * 전체 예약을 예약 시작 일시 내림차순으로 조회합니다.
     *
     * @return 예약 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Reservation> findAll() throws SQLException {
        String sql = """
                SELECT reservation_id
                     , user_id
                     , room_id
                     , start_time
                     , end_time
                     , total_price
                     , status
                FROM reservation
                ORDER BY start_time DESC
                       , reservation_id DESC
                """;

        List<Reservation> reservations = new ArrayList<>();

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                reservations.add(mapReservation(rs));
            }
        }

        return reservations;
    }

    /**
     * 예약 번호로 예약 상세 정보를 조회합니다.
     *
     * @param reservationId 조회할 예약 번호
     * @return 예약 정보. 존재하지 않으면 null
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public Reservation findById(long reservationId) throws SQLException {
        String sql = """
                SELECT reservation_id
                     , user_id
                     , room_id
                     , start_time
                     , end_time
                     , total_price
                     , status
                FROM reservation
                WHERE reservation_id = ?
                """;

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, reservationId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapReservation(rs);
                }

                return null;
            }
        }
    }

    /**
     * 조회 결과의 현재 행을 Reservation 객체로 변환합니다.
     */
    private Reservation mapReservation(ResultSet rs) throws SQLException {
        return Reservation.builder()
                .reservationId(rs.getLong("reservation_id"))
                .userId(rs.getLong("user_id"))
                .roomId(rs.getLong("room_id"))
                .startTime(
                        rs.getTimestamp("start_time").toLocalDateTime()
                )
                .endTime(
                        rs.getTimestamp("end_time").toLocalDateTime()
                )
                .totalPrice(rs.getBigDecimal("total_price"))
                .status(
                        ReservationStatus.valueOf(rs.getString("status"))
                )
                .build();
    }
}
