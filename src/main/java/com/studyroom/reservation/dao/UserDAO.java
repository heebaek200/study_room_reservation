package com.studyroom.reservation.dao;

import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;
import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;


public class UserDAO {



    public User findById(long userId) throws SQLException {
        String sql = """
                SELECT user_id
                     , email
                     , name
                     , role
                     , status
                FROM users
                WHERE user_id = ?;
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
        ) {
            pstmt.setLong(1,userId);
            try (ResultSet rs = pstmt.executeQuery()) {
            if (!rs.next()) {
                return null;
            }
            return User.builder()
                    .userId(rs.getLong("user_id"))
                    .email(rs.getString("email"))
                    .name(rs.getString("name"))
                    .role(UserRole.valueOf(rs.getString("role")))
                    .status(UserStatus.valueOf(rs.getString("status")))
                    .build();
            }
        }

    };

    public int updateName(long userId,String name) throws SQLException {
        String sql = """
                update users 
                set name = ?
                where user_id =  ? AND status = 'ACTIVE';
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);) {
            pstmt.setString(1, name);
            pstmt.setLong(2,userId);
            int rows =  pstmt.executeUpdate();
            return rows;
        }
    };

    public List<User> findAll() throws SQLException {
        List<User> userList = new ArrayList<>();
        String sql = """
                SELECT user_id
                     , email
                     , name
                     , role
                     , status
                FROM users
                ORDER BY user_id;
                """;
        try (Connection conn = DatabaseUtil.getConnection();
             PreparedStatement pstmt = conn.prepareStatement(sql);
             ResultSet rs = pstmt.executeQuery();) {
            while (rs.next()) {
                User user = User.builder()
                        .userId(rs.getLong("user_id"))
                        .email(rs.getString("email"))
                        .name(rs.getString("name"))
                        .role(UserRole.valueOf(rs.getString("role")))
                        .status(UserStatus.valueOf(rs.getString("status")))
                        .build();
                userList.add(user);
            }
        }
        return userList;
    };

    public boolean withdrawIfPossible(long userId) throws SQLException {
        Connection conn = null;
        boolean hasConfirmedReservation;
        String existsSql = """
                SELECT EXISTS (
                    SELECT 1
                    FROM reservation
                    WHERE user_id = ?
                      AND status = 'CONFIRMED'
                ) AS exists_confirmed;
                """;

        String updateSql = """
                UPDATE users
                SET status = 'WITHDRAWN'
                WHERE user_id = ?
                  AND status = 'ACTIVE';
                """;
        try {conn = DatabaseUtil.getConnection();
            conn.setAutoCommit(false);

            try (PreparedStatement existsPstmt = conn.prepareStatement(existsSql)) {
                existsPstmt.setLong(1, userId);
                try (ResultSet rs = existsPstmt.executeQuery()) {
                    rs.next();
                    hasConfirmedReservation = rs.getBoolean("exists_confirmed");
                }
            }
            if (hasConfirmedReservation) {
                conn.rollback();
                return false;
            }

            try (PreparedStatement updatePsmtm = conn.prepareStatement(updateSql)) {
                updatePsmtm.setLong(1, userId);
                int rows = updatePsmtm.executeUpdate();
                if (rows > 0){
                    conn.commit();
                } else {
                    conn.rollback();
                }
                return rows > 0;
            }



        } catch (SQLException e) {
            if (conn != null) {
                conn.rollback();
            }
            throw  e;
        }finally {
            if (conn != null) {
                conn.close();
            }
        }
    };
}
