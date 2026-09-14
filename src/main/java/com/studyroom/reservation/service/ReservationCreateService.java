package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.ReservationCreateDAO;
import com.studyroom.reservation.dao.UserDAO;
import com.studyroom.reservation.dto.Reservation;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.ReservationStatus;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;
import com.studyroom.reservation.util.DatabaseUtil;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDateTime;

/**
 * 회원 예약 생성 Service 클래스
 */
public class ReservationCreateService {

    private final AuthService authService;
    private final UserDAO userDAO;
    private final ReservationCreateDAO reservationCreateDAO;

    public ReservationCreateService(AuthService authService, UserDAO userDAO, ReservationCreateDAO reservationCreateDAO) {
        this.authService = authService;
        this.userDAO = userDAO;
        this.reservationCreateDAO = reservationCreateDAO;
    }

    /**
     * 로그인한 일반 회원의 예약을 생성합니다.
     */
    public void createReservation(String sessionId, long roomId, LocalDateTime startTime, LocalDateTime endTime) throws SQLException {
        LoginSession session = authService.requireLogin(sessionId);

        validateUser(session);
        validateReservationTime(roomId, startTime, endTime);

        try (Connection connection = DatabaseUtil.getConnection()) {
            boolean originalAutoCommit = connection.getAutoCommit();

            try {
                connection.setAutoCommit(false);

                // 스터디룸 존재 여부 확인
                BigDecimal hourlyRate =
                        reservationCreateDAO.findHourlyRateForUpdate(connection, roomId);

                if (hourlyRate == null) {
                    throw new BusinessException("존재하지 않는 스터디룸입니다.");
                }

                // 예약된 시간 확인
                boolean overlapping =
                        reservationCreateDAO.existsOverlappingConfirmed(connection, roomId, startTime, endTime);

                if (overlapping) {
                    throw new BusinessException("이미 예약된 시간입니다.");
                }

                BigDecimal totalPrice = calculateTotalPrice(hourlyRate, startTime, endTime);

                // 예약 작성
                Reservation reservation = Reservation.builder()
                        .userId(session.getUserId())
                        .roomId(roomId)
                        .startTime(startTime)
                        .endTime(endTime)
                        .totalPrice(totalPrice)
                        .status(ReservationStatus.CONFIRMED)
                        .build();

                int rows = reservationCreateDAO.insert(connection, reservation);

                if (rows != 1) {
                    throw new BusinessException(
                            "예약을 저장하지 못했습니다."
                    );
                }

                connection.commit();
            } catch (SQLException | RuntimeException e) {
                connection.rollback();
                throw e;
            } finally {
                connection.setAutoCommit(originalAutoCommit);
            }
        }
    }

    /**
     * 현재 로그인 회원이 예약 가능한 ACTIVE USER인지 확인합니다.
     */
    private void validateUser(LoginSession session) throws SQLException {
        if (session.getRole() != UserRole.USER) {
            throw new BusinessException(
                    "일반 회원만 예약할 수 있습니다."
            );
        }

        User user = userDAO.findById(session.getUserId());

        if (user == null || user.getStatus() != UserStatus.ACTIVE) {
            throw new BusinessException(
                    "예약할 수 없는 회원입니다."
            );
        }
    }

    /**
     * 스터디룸 번호와 예약 시간을 검증합니다.
     */
    private void validateReservationTime(long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        if (roomId <= 0) {
            throw new BusinessException(
                    "올바른 스터디룸을 선택해 주세요."
            );
        }

        if (startTime == null || endTime == null) {
            throw new BusinessException(
                    "예약 시작 일시와 종료 일시를 입력해 주세요."
            );
        }

        if (!endTime.isAfter(startTime)) {
            throw new BusinessException(
                    "종료 일시는 시작 일시보다 늦어야 합니다."
            );
        }
    }

    /**
     * 총 이용 금액을 계산합니다.
     *
     * (설계 보완) 분 단위 이용 시간을 기준으로 총 이용 금액을 계산합니다.
     */
    private BigDecimal calculateTotalPrice(BigDecimal hourlyRate, LocalDateTime startTime, LocalDateTime endTime) {
        long durationMinutes = Duration.between(startTime, endTime).toMinutes();

        return hourlyRate
                .multiply(BigDecimal.valueOf(durationMinutes))
                .divide(BigDecimal.valueOf(60), 2, RoundingMode.HALF_UP);
    }
}