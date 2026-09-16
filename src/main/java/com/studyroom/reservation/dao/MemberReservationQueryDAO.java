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
import java.util.Optional;

public class MemberReservationQueryDAO {

    /**
     * 특정 회원의 모든 예약 목록을 최신순으로 조회
     *
     * @param memberId 조회할 회원의 ID
     * @return 회원의 예약 목록
     */
    public List<Reservation> findByMemberId(String memberId) {

        //[1] SQL 작성: 데이터베이스에 전달할 명령어를 문자열로 준비하기
        // 물음표(?)는 나중에 실제 사용자 ID로 치워넣을 빈칸(바인딩 변수) 역할을 함
        String sql = """
                SELECT reservation_id, user_id, room_id, start_time, end_time, total_price, status
                FROM reservation
                WHERE user_id = ?
                ORDER BY start_time DESC
                """;

        //[2] 리스트 생성: 데이터베이스에서 꺼내온 여러 개의 예약 정보를 차곡차곡 담을 빈 바구니 만들기
        List<Reservation> reservations = new ArrayList<>();

        //[3] try-with-resource 구문: 소괄호() 안에서 획득한 자원(DB 연결 객체 등)을
        //처리가 끝난 후 자동으로 반납(close)해주는 안전한 문법
        //[4] DB 연결: 유틸리티 클래스를 사용해 데이터베이스와 통신할 수 있는 길(Connection) 열기
        try (Connection conn = DatabaseUtil.getConnection();

             //[5] 쿼리 준비: 열린 길을 통해 위에서 짠 SQL문을 싳고 갈 트럭(PreparedStatement)을 준비
             PreparedStatement pstmt = conn.prepareStatement(sql)
        ) {

        //[6] 값 채우기: SQL 문의 첫번째(1) 물음표 자리에 이 메서드가 넘겨받은 memberId(문자열) 셋팅
            pstmt.setString(1,memberId);

        //[7] 쿼리 실행 및 결과 받기: executeQuery()는 준비된 쿼리를 DB에 실행하고
        // 그 결과로 나온 표 형태의 데이터(ResultSet)를 반환받음
            try (ResultSet rs = pstmt.executeQuery()) {

                //[8] 데이터 순회: rs.next()는 '다음 중(행)에 읽을 데이터가 있니?'하고 묻는 명령어
                // 데이터가 있으면 true를 반환하여 while문을 실행하고, 없으면 false를 반환해 멈춤
                while(rs.next()) {
                    //한 줄씩 읽은 데이터를 Reservation 자바 객체로 변환하여 아까 만든 바구니(List)에 넣기
                    reservations.add(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            // DB 통신 중 문제가 발생하면 (SQLException), 어떤 작업 중 문제인지 파악하기 쉽게
            // RuntimeException으로 포장해서 밖으로 던짐
            throw new RuntimeException("회원 예약 목록 조회 중 DB 오류가 발생했습니다." + e);
        }
        // 꺼낸 정보를 자바 객체로 만들어서 바구니에 차곡차곡 담아냄
        return reservations;
    }
    public Optional<Reservation> findById(Long id) {
        // 단건 조회 쿼리 준비
        String sql = """
                SELECT reservation_id, user_id, room_id, start_time, end_time, total_price, status
                FROM reservation 
                WHERE reservation_id = ?
                """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // 이번에는 들어온 값이 Long 타입 숫자이므로 setString 대신 setLong을 사용해 첫 번째 물음표를 채우기.
            pstmt.setLong(1, id);

            try (ResultSet rs = pstmt.executeQuery()) {
                // 목록 조회가 아니므로 while 대신 if를 사용하기.
                // '데이터가 하나라도 존재하는가?'를 확인함.
                if (rs.next()) {
                    // 데이터가 존재하면 변환 후 Optional.of()로 예쁘게 포장해서 반환.
                    return Optional.of(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            throw new RuntimeException("예약 상세 조회 중 DB 오류가 발생했습니다.", e);
        }

        // if문을 통과하지 못했다면 데이터가 없는 것이므로, '비어있음'을 의미하는 Optional.empty()를 반환.
        return Optional.empty();
    }


    // DB의 표 데이터(ResultSet) 한 줄을 받아서 Java 객체(Reservation)로 변환해주는 메서드.
    private Reservation mapRowToReservation(ResultSet rs) throws SQLException {
        Reservation reservation = new Reservation();

        // rs.getLong("컬럼명"): 현재 읽고 있는 줄에서 해당 이름의 컬럼 값을 Long(큰 정수) 타입으로 꺼내오기.
        // 그리고 꺼낸 값을 Reservation 객체의 setter 메서드를 통해 저장.
        reservation.setReservationId(rs.getLong("reservation_id"));
        reservation.setUserId(rs.getLong("user_id"));

        // rs.getString("컬럼명"): 해당 컬럼 값을 String(문자열) 타입으로 꺼내오기.
        reservation.setRoomId(rs.getLong("room_id"));

        // 상태값(status)이 비어있지 않다면, DB의 문자열(예: "CONFIRMED")을 Java의 Enum 타입으로 변환.
        if (rs.getString("status") != null) {
            reservation.setStatus(ReservationStatus.valueOf(rs.getString("status")));
        }

        // rs.getTimestamp("컬럼명"): 날짜/시간 데이터를 꺼내오기.
        // Java 최신 표준인 LocalDateTime 타입으로 변환(.toLocalDateTime())하여 객체에 저장.
        if (rs.getTimestamp("start_time") != null) {
            reservation.setStartTime(rs.getTimestamp("start_time").toLocalDateTime());
        }
        if (rs.getTimestamp("end_time") != null) {
            reservation.setEndTime(rs.getTimestamp("end_time").toLocalDateTime());
        }

        // total_price는 소수점이 있는 금액이라 int가 아닌 BigDecimal로 꺼내와 세팅.
        reservation.setTotalPrice(rs.getBigDecimal("total_price"));

        return reservation;
    }

    /**
     * 예약 ID와 회원 ID를 모두 조건으로 사용하여 특정 예약을 단건 조회합니다.
     * (조회와 동시에 본인의 예약인지 검증하는 효과를 가집니다.)
     *
     * @param reservationId 조회할 예약의 PK (DB의 reservation_id)
     * @param userId 조회를 요청한 회원의 PK (DB의 user_id)
     * @return 예약 정보 (본인의 예약이 아니거나 존재하지 않으면 Optional.empty() 반환)
     */
    public Optional<Reservation> findByIdAndUserId(Long reservationId, Long userId) {

        // [1] SQL 작성: reservation_id와 user_id가 모두 일치(AND)하는 데이터만 조회합니다.
        String sql = """ 
                SELECT reservation_id, user_id, room_id, start_time, end_time, total_price, status 
                FROM reservation 
                WHERE reservation_id = ? AND user_id = ? 
                """;

        // [2] DB 연결 및 자원 해제 준비 (try-with-resources)
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            // [3] 값 채우기: 두 개의 물음표(?) 자리에 각각 예약 ID와 회원 ID를 세팅합니다.
            pstmt.setLong(1, reservationId);
            pstmt.setLong(2, userId);

            // [4] 쿼리 실행 및 결과 처리
            try (ResultSet rs = pstmt.executeQuery()) {

                // 데이터가 하나라도 존재한다면 (예약이 존재하고, 동시에 내 예약이 맞다면)
                if (rs.next()) {
                    // 헬퍼 메서드를 통해 자바 객체로 변환한 뒤 포장해서 반환합니다.
                    return Optional.of(mapRowToReservation(rs));
                }
            }
        } catch (SQLException e) {
            // DB 오류 발생 시 예외 처리
            throw new RuntimeException("예약 상세 조회 및 권한 확인 중 DB 오류가 발생했습니다.", e);
        }

        // 조건에 맞는 데이터가 없다면 빈 Optional을 반환합니다.
        // (예약이 아예 없거나, 다른 사람의 예약인 경우 모두 이곳으로 도달합니다.)
        return Optional.empty();
    }
}
