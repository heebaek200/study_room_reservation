package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.StudyRoom;
import com.studyroom.reservation.util.DatabaseUtil;
import com.studyroom.reservation.exception.BusinessException;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

public class StudyRoomDAO {

    /**
     * 1. 목록 조회
     * 조회 결과가 없으면 null을 반환합니다.
     */
    public List<StudyRoom> findAll() {
        String sql = """
                    SELECT room_id
                         , name
                         , capacity
                         , hourly_rate
                    FROM study_room
                    ORDER BY room_id
                    """;
        List<StudyRoom> studyRooms = new ArrayList<>();

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery()) {

            while (rs.next()) {
                StudyRoom room = new StudyRoom();
                room.setRoomId(rs.getLong("room_id"));
                room.setName(rs.getString("name"));
                room.setCapacity(rs.getInt("capacity"));
                room.setHourlyRate(rs.getBigDecimal("hourly_rate"));

                studyRooms.add(room);
            }

            return studyRooms.isEmpty() ? null : studyRooms;

        } catch (SQLException e) {
            e.printStackTrace();
            return null;
        }
    }

    /**
     * 2. 상세 조회
     * 조회 결과가 없으면 null을 반환합니다.
     */
    public StudyRoom findById(long roomId) {
        String sql = """
                    SELECT room_id
                         , name
                         , capacity
                         , hourly_rate
                    FROM study_room
                    WHERE room_id = ?
                    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setLong(1, roomId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    StudyRoom room = new StudyRoom();
                    room.setRoomId(rs.getLong("room_id"));
                    room.setName(rs.getString("name"));
                    room.setCapacity(rs.getInt("capacity"));
                    room.setHourlyRate(rs.getBigDecimal("hourly_rate"));

                    return room;
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }

        return null;
    }

    /**
     * 3. 이름 중복 검사
     */
    public boolean existsByName(String name) {
        String sql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM study_room
                        WHERE name = ?
                    ) AS exists_name
                    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("exists_name"); // EXISTS 결과(0 또는 1)를 boolean으로 반환
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 4. 특정 ID를 제외하고 이름 중복 검사 (수정 시)
     */
    public boolean existsByNameExceptRoomId(String name, long roomId) {
        String sql = """
                    SELECT EXISTS (
                        SELECT 1
                        FROM study_room
                        WHERE name = ?
                          AND room_id <> ?
                    ) AS exists_name
                    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, name);
            pstmt.setLong(2, roomId);

            try (ResultSet rs = pstmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getBoolean("exists_name");
                }
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }
        return false;
    }

    /**
     * 5. 스터디룸 등록 (Insert)
     * 생성된 room_id 반환 없이 성공적으로 반영된 행의 수(1)만 반환
     */
    public int insert(StudyRoom studyRoom) {
        String sql = """
                    INSERT INTO study_room (
                        name
                      , capacity
                      , hourly_rate
                    ) VALUES (
                        ?
                      , ?
                      , ?
                    )
                    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, studyRoom.getName());
            pstmt.setInt(2, studyRoom.getCapacity());
            pstmt.setBigDecimal(3, studyRoom.getHourlyRate());

            return pstmt.executeUpdate();

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }

    /**
     * 6. 스터디룸 수정 (Update)
     * 변경된 데이터가 없으면 BusinessException 발생
     */
    public int update(StudyRoom studyRoom) {
        String sql = """  
                    UPDATE study_room
                    SET name = ?
                      , capacity = ?
                      , hourly_rate = ?
                    WHERE room_id = ?
                    """;

        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql)) {

            pstmt.setString(1, studyRoom.getName());
            pstmt.setInt(2, studyRoom.getCapacity());
            pstmt.setBigDecimal(3, studyRoom.getHourlyRate());
            pstmt.setLong(4, studyRoom.getRoomId());

            int rows = pstmt.executeUpdate();

            if (rows == 0) {
                throw new BusinessException("수정할 스터디룸을 찾을 수 없습니다.");
            }

            return rows;

        } catch (SQLException e) {
            e.printStackTrace();
            return 0;
        }
    }
}
