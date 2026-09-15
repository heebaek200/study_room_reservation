package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.ReservationCancelDAO;
import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.enums.ReservationStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.SQLException;

public class ReservationCancelService {

    private final AuthService authService;
    private final ReservationCancelDAO reservationCancelDAO;

    public ReservationCancelService(AuthService authService, ReservationCancelDAO reservationCancelDAO) {
        this.authService = authService;
        this.reservationCancelDAO = reservationCancelDAO;
    }

    public int cancelReservation(String sessionId, long reservationId) throws SQLException {
        // 로그인 되었는지 확인
        LoginSession session = authService.requireLogin(sessionId);

        try (Connection conn = DatabaseUtil.getConnection()) {
            boolean originalAutoCommit = conn.getAutoCommit();

            try {
                conn.setAutoCommit(false);

                Reservation reservation = reservationCancelDAO.findForUpdate(conn, reservationId);

                if (reservation == null) {
                    throw new BusinessException("존재하지 않는 예약입니다.");
                }

                if (!reservation.getUserId().equals(session.getUserId())) {
                    throw new BusinessException("본인 예약만 취소할 수 있습니다.");
                }

                if (reservation.getStatus() != ReservationStatus.CONFIRMED) {
                    throw new BusinessException("확정된 예약만 취소할 수 있습니다.");
                }

                int cancelRows = reservationCancelDAO.cancel(conn, reservationId);

                if (cancelRows <= 0) {
                    throw new BusinessException("예약 취소에 실패하였습니다.");
                }

                int insertRows = reservationCancelDAO.insertRefundRequest(conn, reservationId);

                if (insertRows <= 0) {
                    throw new BusinessException("환불 요청 생성에 실패하였습니다.");
                }

                conn.commit();
                return cancelRows;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }
}
