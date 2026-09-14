package com.studyroom.reservation;

import com.studyroom.reservation.dao.MemberReservationQueryDAO;
import com.studyroom.reservation.dto.Reservation;

import java.util.List;
import java.util.Optional;

public class DaoTestMain {

    public static void main(String[] args) {
        // Service를 거치지 않고 DAO 객체 직접 생성
        MemberReservationQueryDAO dao = new MemberReservationQueryDAO();

        System.out.println("========================================");
        System.out.println("1. 특정 회원의 예약 목록 조회 테스트 (findByMemberId)");
        System.out.println("========================================");
        try {
            String testUserId = "11";
            List<Reservation> reservations = dao.findByMemberId(testUserId);

            System.out.println("조회된 예약 건수: " + reservations.size() + "건");
            for (Reservation r : reservations) {
                System.out.println("- 예약번호: " + r.getReservationId() +
                        ", 방번호: " + r.getRoomId() +
                        ", 상태: " + r.getStatus());
            }
        } catch (Exception e) {
            System.out.println("❌ 에러 발생: " + e.getMessage());
        }

        System.out.println("\n========================================");
        System.out.println("2. 예약 단건 조회 테스트 (findById)");
        System.out.println("========================================");
        try {
            Long testReservationId = 1L; // 실제 DB에 존재하는 예약 ID로 변경하세요
            Optional<Reservation> reservationOpt = dao.findById(testReservationId);

            if (reservationOpt.isPresent()) {
                Reservation r = reservationOpt.get();
                System.out.println("✅ 조회 성공! 예약번호: " + r.getReservationId() +
                        ", 소유자 회원번호: " + r.getUserId());
            } else {
                System.out.println("ℹ️ 해당하는 예약이 존재하지 않습니다.");
            }
        } catch (Exception e) {
            System.out.println("❌ 에러 발생: " + e.getMessage());
        }

        System.out.println("\n========================================");
        System.out.println("3. 예약 ID + 회원 ID 동시 검증 조회 테스트 (findByIdAndUserId)");
        System.out.println("========================================");
        try {
            Long testReservationId = 4L;
            Long testUserId = 32L; // 일치하는 소유자 ID

            Optional<Reservation> reservationOpt = dao.findByIdAndUserId(testReservationId, testUserId);

            if (reservationOpt.isPresent()) {
                System.out.println("✅ 본인 예약 확인 및 상세 조회 성공!");
            } else {
                System.out.println("🛡️ 데이터가 없거나, 다른 회원의 예약이라 차단되었습니다.");
            }
        } catch (Exception e) {
            System.out.println("❌ 에러 발생: " + e.getMessage());
        }
    }
}