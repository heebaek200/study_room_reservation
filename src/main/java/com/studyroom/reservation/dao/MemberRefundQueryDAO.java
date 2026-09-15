package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.enums.RefundStatus;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * 일반 회원의 본인 환불 내역 조회를 담당합니다.
 *
 * 회원 권한과 로그인 여부 확인은 Service에서 처리합니다.
 */
public class MemberRefundQueryDAO {

    /**
     * 지정한 회원이 생성한 예약에 연결된 환불 내역을 조회합니다.
     *
     * refund 테이블에는 user_id가 없으므로
     * reservation 테이블과 JOIN하여 회원 소유권을 확인합니다.
     *
     * @param userId 환불 내역을 조회할 회원 번호
     * @return 회원의 환불 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Refund> findByUserId(long userId)
            throws SQLException {

        String sql = """
                SELECT refund.refund_id
                     , refund.reservation_id
                     , refund.status
                FROM refund
                INNER JOIN reservation
                        ON reservation.reservation_id
                        = refund.reservation_id
                WHERE reservation.user_id = ?
                ORDER BY refund.refund_id DESC
                """;

        List<Refund> refunds = new ArrayList<>();

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, userId);

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    refunds.add(mapRefund(rs));
                }
            }
        }

        return refunds;
    }

    /**
     * 조회 결과의 현재 행을 Refund 객체로 변환합니다.
     */
    private Refund mapRefund(ResultSet rs)
            throws SQLException {

        return Refund.builder()
                .refundId(rs.getLong("refund_id"))
                .reservationId(rs.getLong("reservation_id"))
                .status(
                        RefundStatus.valueOf(
                                rs.getString("status")
                        )
                )
                .build();
    }
}
