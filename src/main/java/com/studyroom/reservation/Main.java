package com.studyroom.reservation;

import com.studyroom.reservation.util.DatabaseUtil;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

//TIP 코드를 <b>실행</b>하려면 <shortcut actionId="Run"/>을(를) 누르거나
// 에디터 여백에 있는 <icon src="AllIcons.Actions.Execute"/> 아이콘을 클릭하세요.
public class Main {
    public static void main(String[] args) {

        try (Connection connection = DatabaseUtil.getConnection()) {

            PreparedStatement statement = connection.prepareStatement("""
SELECT * FROM users LIMIT 30 
""");

            ResultSet rs = statement.executeQuery();
            while (rs.next()) {
                System.out.println(rs.getString("name"));
            }

        } catch (SQLException e) {
            throw new RuntimeException(e);
        }

    }
}