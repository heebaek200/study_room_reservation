package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.enums.ReservationStatus;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

/**
 * 예약 취소 및 환불 요청 생성 DAO
 */
public class ReservationCancelDAO {

    /**
     * 취소 대상 예약을 잠그고(FOR UPDATE) 소유자·상태를 조회합니다.
     * 존재하지 않으면 null을 반환합니다.
     */
    public Reservation findForUpdate(Connection conn, Long reservationId) throws SQLException {
        String sql = """
                select reservation_id, user_id, status
                from reservation
                where reservation_id =?
                for update
                """;

        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1,reservationId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return Reservation.builder()
                        .reservationId(rs.getLong("reservation_id"))
                        .userId(rs.getLong("user_id"))
                        .status(ReservationStatus.valueOf(rs.getString("status")))
                        .build();
            }
        }
    }

    /**
     * 예약 상태를 CANCELLED로 변경합니다.
     * CONFIRMED 상태인 경우에만 적용되며, 변경된 행 수를 반환합니다.
     */
    public int cancel(Connection conn,long reservationId) throws SQLException {
        String sql = """
                update reservation 
                set status = 'CANCELLED'
                where reservation_id = ? and status = 'CONFIRMED'
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1,reservationId);
            return pstmt.executeUpdate();
        }
    }

    /**
     * REQUESTED 상태의 환불 요청을 생성합니다.
     */
    public int insertRefundRequest(Connection conn, Long reservationId) throws SQLException {
        String sql = """
                insert into refund (reservation_id, status)
                values (?,'REQUESTED')
                """;
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setLong(1,reservationId);
            return pstmt.executeUpdate();
        }
    }

}
