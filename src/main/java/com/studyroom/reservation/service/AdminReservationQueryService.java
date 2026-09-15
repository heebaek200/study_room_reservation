package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.AdminReservationQueryDAO;
import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;

import java.sql.SQLException;
import java.util.List;

/**
 * 관리자의 전체 예약 목록·상세 조회를 처리합니다.
 */
public class AdminReservationQueryService {

    private final AuthService authService;
    private final AdminReservationQueryDAO adminReservationQueryDAO;

    public AdminReservationQueryService(
            AuthService authService,
            AdminReservationQueryDAO adminReservationQueryDAO
    ) {
        this.authService = authService;
        this.adminReservationQueryDAO = adminReservationQueryDAO;
    }

    /**
     * 관리자에게 전체 예약 목록을 반환합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @return 예약 목록. 조회 결과가 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public List<Reservation> getReservations(String sessionId)
            throws SQLException {
        requireAdmin(sessionId);

        return adminReservationQueryDAO.findAll();
    }

    /**
     * 관리자에게 특정 예약의 상세 정보를 반환합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @param reservationId 조회할 예약 번호
     * @return 예약 상세 정보
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     */
    public Reservation getReservation(
            String sessionId,
            long reservationId
    ) throws SQLException {
        requireAdmin(sessionId);

        if (reservationId <= 0) {
            throw new BusinessException(
                    "올바른 예약 번호를 입력해 주세요."
            );
        }

        Reservation reservation =
                adminReservationQueryDAO.findById(reservationId);

        if (reservation == null) {
            throw new BusinessException(
                    "예약을 찾을 수 없습니다."
            );
        }

        return reservation;
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
}
