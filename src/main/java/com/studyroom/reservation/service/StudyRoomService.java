package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.StudyRoomDAO;
import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;

import java.math.BigDecimal;
import java.util.List;

public class StudyRoomService {

    private final StudyRoomDAO studyRoomDAO;
    private final AuthService authService;

    // 생성자를 통한 의존성 주입 (필요에 따라 싱글톤 패턴 등으로 조정 가능)
    public StudyRoomService(StudyRoomDAO studyRoomDAO, AuthService authService) {
        this.studyRoomDAO = studyRoomDAO;
        this.authService = authService;
    }

    /**
     * 1. 목록 조회 (세션 요구 안 함)
     */
    public List<StudyRoom> getRooms() {
        return studyRoomDAO.findAll();
    }

    /**
     * 2. 상세 조회 (세션 요구 안 함)
     */
    public StudyRoom getRoom(long roomId) {
        StudyRoom room = studyRoomDAO.findById(roomId);
        if (room == null) {
            throw new BusinessException("스터디룸을 찾을 수 없습니다.");
        }
        return room;
    }

    /**
     * 3. 스터디룸 등록
     */
    public void createRoom(String sessionId, String name, int capacity, BigDecimal hourlyRate) {
        // 1) 권한 검사
        checkAdminRole(sessionId);

        // 2) 입력값 검사
        validateInput(name, capacity, hourlyRate);

        // 3) 이름 중복 검사
        if (studyRoomDAO.existsByName(name)) {
            throw new BusinessException("이미 동일한 이름의 스터디룸이 존재합니다.");
        }

        // 4) 등록 처리
        StudyRoom room = new StudyRoom();
        room.setName(name);
        room.setCapacity(capacity);
        room.setHourlyRate(hourlyRate);

        int rows = studyRoomDAO.insert(room);

        if (rows != 1) {
            throw new BusinessException("스터디룸 등록에 실패했습니다.");
        }
    }

    /**
     * 4. 스터디룸 수정
     */
    public void updateRoom(String sessionId, long roomId, String name, int capacity, BigDecimal hourlyRate) {
        // 1) 권한 검사
        checkAdminRole(sessionId);

        // 2) 입력값 검사
        validateInput(name, capacity, hourlyRate);

        // 3) 자신을 제외한 이름 중복 검사
        if (studyRoomDAO.existsByNameExceptRoomId(name, roomId)) {
            throw new BusinessException("이미 동일한 이름의 스터디룸이 존재합니다.");
        }

        // 4) 수정 처리 (DAO 내에서 수정된 행이 0개면 예외를 던지도록 이미 구현됨)
        StudyRoom room = new StudyRoom();
        room.setRoomId(roomId);
        room.setName(name);
        room.setCapacity(capacity);
        room.setHourlyRate(hourlyRate);

        studyRoomDAO.update(room);
    }

    /* =========================================================
       내부 검증용 Helper 메서드
       ========================================================= */

    /**
     * 관리자 권한 확인
     */
    private void checkAdminRole(String sessionId) {
        LoginSession session = authService.requireLogin(sessionId);
        if (session.getRole() != UserRole.ADMIN) {
            throw new BusinessException("관리자 권한이 필요합니다.");
        }
    }

    /**
     * 공통 입력값 검사
     */
    private void validateInput(String name, int capacity, BigDecimal hourlyRate) {
        // name: null 또는 공백 금지
        if (name == null || name.trim().isEmpty()) {
            throw new BusinessException("스터디룸 이름은 필수 입력값입니다.");
        }

        // capacity: 0보다 커야 함
        if (capacity <= 0) {
            throw new BusinessException("수용 인원은 0보다 커야 합니다.");
        }

        // hourlyRate: null 금지, 0 이상
        if (hourlyRate == null || hourlyRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new BusinessException("시간당 이용료는 0 이상이어야 합니다.");
        }
    }
}
