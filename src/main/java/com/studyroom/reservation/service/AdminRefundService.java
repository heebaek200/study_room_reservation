package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.AdminRefundDAO;
import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.enums.RefundStatus;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * 관리자의 환불 조회를 처리합니다.
 */
public class AdminRefundService {

    private final AuthService authService;
    private final AdminRefundDAO adminRefundDAO;

    public AdminRefundService(
            AuthService authService,
            AdminRefundDAO adminRefundDAO
    ) {
        this.authService = authService;
        this.adminRefundDAO = adminRefundDAO;
    }

    /**
     * 관리자에게 전체 환불 목록을 반환합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @return 환불 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Refund> getRefunds(String sessionId)
            throws SQLException {
        requireAdmin(sessionId);

        return adminRefundDAO.findAll();
    }

    /**
     * 관리자에게 지정한 상태의 환불 목록을 반환합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @param status 조회할 환불 상태
     * @return 환불 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Refund> getRefundsByStatus(
            String sessionId,
            RefundStatus status
    ) throws SQLException {
        requireAdmin(sessionId);

        if (status == null) {
            throw new BusinessException(
                    "조회할 환불 상태를 선택해 주세요."
            );
        }

        return adminRefundDAO.findByStatus(status);
    }

    /**
     * 관리자에게 특정 환불의 상세 정보를 반환합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @param refundId 조회할 환불 번호
     * @return 환불 상세 정보
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public Refund getRefund(
            String sessionId,
            long refundId
    ) throws SQLException {
        requireAdmin(sessionId);

        if (refundId <= 0) {
            throw new BusinessException(
                    "올바른 환불 번호를 입력해 주세요."
            );
        }

        Refund refund = adminRefundDAO.findById(refundId);

        if (refund == null) {
            throw new BusinessException(
                    "환불 요청을 찾을 수 없습니다."
            );
        }

        return refund;
    }

    /**
     * 로그인 여부와 관리자 권한을 확인합니다.
     */
    private void requireAdmin(String sessionId) {
        LoginSession session = authService.requireLogin(sessionId);

        if (session.getRole() != UserRole.ADMIN) {
            throw new BusinessException(
                    "관리자 권한이 필요합니다."
            );
        }
    }


    /**
     * 대기 중인 환불 요청을 승인합니다.
     */
    public void approveRefund(
            String sessionId,
            long refundId
    ) throws SQLException {
        processRefund(sessionId, refundId, RefundStatus.APPROVED);
    }

    /**
     * 대기 중인 환불 요청을 거절합니다.
     */
    public void rejectRefund(
            String sessionId,
            long refundId
    ) throws SQLException {
        processRefund(sessionId, refundId, RefundStatus.REJECTED);
    }

    /**
     * 관리자 권한과 환불 상태를 확인하고 승인·거절을 처리합니다.
     */
    private void processRefund(
            String sessionId,
            long refundId,
            RefundStatus targetStatus
    ) throws SQLException {
        requireAdmin(sessionId);

        if (refundId <= 0) {
            throw new BusinessException(
                    "올바른 환불 번호를 입력해 주세요."
            );
        }

        if (targetStatus != RefundStatus.APPROVED
                && targetStatus != RefundStatus.REJECTED) {
            throw new BusinessException(
                    "환불은 승인 또는 거절로만 처리할 수 있습니다."
            );
        }

        try (Connection connection = DatabaseUtil.getConnection()) {
            // 이 Connection은 이번 처리 전용으로 사용합니다.
            connection.setAutoCommit(false);

            try {
                Refund refund = adminRefundDAO.findByIdForUpdate(
                        connection,
                        refundId
                );

                if (refund == null) {
                    throw new BusinessException(
                            "환불 요청을 찾을 수 없습니다."
                    );
                }

                if (refund.getStatus() != RefundStatus.REQUESTED) {
                    throw new BusinessException(
                            "이미 처리된 환불 요청입니다."
                    );
                }

                int rows = adminRefundDAO.updateStatus(
                        connection,
                        refundId,
                        targetStatus
                );

                if (rows != 1) {
                    throw new BusinessException(
                            "환불 요청을 처리하지 못했습니다."
                    );
                }

                connection.commit();
            } catch (SQLException | RuntimeException e) {
                try {
                    connection.rollback();
                } catch (SQLException rollbackException) {
                    // 기존 프로젝트 방식에 맞춰 Rollback 오류만 출력합니다.
                    rollbackException.printStackTrace();
                }

                // 최초 발생한 원래 예외를 유지합니다.
                throw e;
            }
        }
    }

}
