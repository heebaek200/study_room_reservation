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
 * 관리자의 환불 조회를 담당합니다.
 * 관리자 권한 검사는 이 DAO를 호출하는 Service에서 처리합니다.
 */
public class AdminRefundDAO {

    /**
     * 전체 환불 요청을 환불 번호 내림차순으로 조회합니다.
     *
     * @return 환불 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Refund> findAll() throws SQLException {
        String sql = """
                SELECT refund_id
                     , reservation_id
                     , status
                FROM refund
                ORDER BY refund_id DESC
                """;

        List<Refund> refunds = new ArrayList<>();

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                refunds.add(mapRefund(rs));
            }
        }

        return refunds;
    }

    /**
     * 지정한 상태의 환불 요청을 조회합니다.
     *
     * @param status 조회할 환불 상태
     * @return 환불 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Refund> findByStatus(RefundStatus status)
            throws SQLException {
        String sql = """
                SELECT refund_id
                     , reservation_id
                     , status
                FROM refund
                WHERE status = ?
                ORDER BY refund_id DESC
                """;

        List<Refund> refunds = new ArrayList<>();

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setString(1, status.name());

            try (ResultSet rs = pstmt.executeQuery()) {
                while (rs.next()) {
                    refunds.add(mapRefund(rs));
                }
            }
        }

        return refunds;
    }

    /**
     * 환불 번호로 상세 정보를 조회합니다.
     *
     * @param refundId 조회할 환불 번호
     * @return 환불 정보. 존재하지 않으면 null
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public Refund findById(long refundId) throws SQLException {
        String sql = """
                SELECT refund_id
                     , reservation_id
                     , status
                FROM refund
                WHERE refund_id = ?
                """;

        try (Connection connection = DatabaseUtil.getConnection();
             PreparedStatement pstmt = connection.prepareStatement(sql)) {

            pstmt.setLong(1, refundId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRefund(rs);
                }

                return null;
            }
        }
    }

    /**
     * 조회 결과의 현재 행을 Refund 객체로 변환합니다.
     */
    private Refund mapRefund(ResultSet rs) throws SQLException {
        return Refund.builder()
                .refundId(rs.getLong("refund_id"))
                .reservationId(rs.getLong("reservation_id"))
                .status(
                        RefundStatus.valueOf(rs.getString("status"))
                )
                .build();
    }


    /**
     * 환불 행을 잠그고 상세 정보를 조회합니다.
     *
     * 호출하는 Service에서 AutoCommit을 false로 설정해야 합니다.
     * 잠금은 해당 트랜잭션의 Commit 또는 Rollback까지 유지됩니다.
     *
     * @param connection Service에서 전달한 트랜잭션 Connection
     * @param refundId 처리할 환불 번호
     * @return 환불 정보. 존재하지 않으면 null
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public Refund findByIdForUpdate(
            Connection connection,
            long refundId
    ) throws SQLException {
        String sql = """
            SELECT refund_id
                 , reservation_id
                 , status
            FROM refund
            WHERE refund_id = ?
            FOR UPDATE
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setLong(1, refundId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return mapRefund(rs);
                }

                return null;
            }
        }
    }

    /**
     * 대기 중인 환불 요청을 승인 또는 거절 상태로 변경합니다.
     *
     * Service에서 변경할 상태가 APPROVED 또는 REJECTED인지 검증한 뒤
     * findByIdForUpdate()와 동일한 Connection으로 호출합니다.
     *
     * @param connection Service에서 전달한 트랜잭션 Connection
     * @param refundId 처리할 환불 번호
     * @param status 변경할 환불 상태
     * @return 변경한 행 수. 대상이 없거나 REQUESTED 상태가 아니면 0
     * @throws SQLException DB 수정 중 오류가 발생한 경우
     */
    public int updateStatus(
            Connection connection,
            long refundId,
            RefundStatus status
    ) throws SQLException {
        String sql = """
            UPDATE refund
            SET status = ?
            WHERE refund_id = ?
              AND status = 'REQUESTED'
            """;

        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, status.name());
            pstmt.setLong(2, refundId);

            return pstmt.executeUpdate();
        }
    }
}
