package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.MemberReservationQueryDAO;
import com.studyroom.reservation.dto.Reservation;

import java.util.List;
import java.util.Optional;

public class MemberReservationQueryService {

    private final MemberReservationQueryDAO dao;

    // 생성자를 통해 DAO를 주입받아 사용합니다. (이름 변경 적용)
    public MemberReservationQueryService(MemberReservationQueryDAO dao) {
        this.dao = dao;
    }

    /**
     * [요구사항 충족] 회원별 예약 목록 조회
     * 일반 회원이 본인의 전체 예약 내역을 확인합니다.
     *
     * @param loginUserId 현재 로그인한 회원의 ID
     * @return 본인의 예약 목록 리스트
     */
    public List<Reservation> getMyReservations(Long loginUserId) {
        // 1. 로그인 회원 확인 (방어 로직)
        if (loginUserId == null) {
            throw new IllegalArgumentException("로그인 정보가 유효하지 않습니다.");
        }

        // DAO의 findByMemberId는 String을 받으므로 변환하여 넘겨줍니다.
        return dao.findByMemberId(String.valueOf(loginUserId));
    }

    /**
     * [요구사항 충족] 예약 상세 조회 & 관리자/일반 회원 조회 범위 구분
     *
     * @param reservationId 조회할 예약 번호
     * @param loginUserId   현재 로그인한 회원의 ID
     * @param role          현재 로그인한 회원의 권한 ("USER" 또는 "ADMIN")
     * @return 예약 정보 (조회 불가 시 Optional.empty())
     */
    public Optional<Reservation> getReservationDetail(Long reservationId, Long loginUserId, String role) {

        // 1. 로그인 회원 확인
        if (loginUserId == null || role == null) {
            // 비회원(null)이 접근하면 여기서 즉시 에러를 터뜨리고 메서드를 강제 종료합니다!
            throw new IllegalArgumentException("권한이 없는 사용자입니다.");
        }

        // 2. 관리자(ADMIN)인 경우: 다른 회원의 예약 조회 차단 해제 (모두 조회 가능)
        // -> 소유자를 검증하지 않는 findById를 호출합니다.
        if ("ADMIN".equalsIgnoreCase(role)) {
            System.out.println("🛡️ [System] 관리자 권한으로 예약(ID: " + reservationId + ")을 조회합니다.");
            return dao.findById(reservationId);
        }

        // 3. 일반 회원(USER)인 경우: 로그인 회원과 예약 소유자 확인 (본인 것만 조회)
        // -> 소유자 검증 로직이 포함된 findByIdAndUserId를 호출합니다.
        // 이 과정에서 다른 회원의 예약 조회는 DAO 레벨에서 자동으로 차단됩니다.
        System.out.println("👤 [System] 일반 회원 권한으로 본인 소유의 예약(ID: " + reservationId + ")을 조회합니다.");
        return dao.findByIdAndUserId(reservationId, loginUserId);
    }
}
