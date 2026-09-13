package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.UserDAO;
import com.studyroom.reservation.dto.User;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;

import java.sql.SQLException;
import java.util.List;

public class UserService {
    private final AuthService authService;
    private final UserDAO userDAO;

    public UserService(AuthService authService, UserDAO userDAO){
        this.authService = authService;
        this.userDAO = userDAO;
    }



    public User getMyInfo(String sessionId) throws SQLException {
        LoginSession session = authService.requireLogin(sessionId);
        return userDAO.findById(session.getUserId());
    };

    public void updateMyName(String sessionId, String name) throws SQLException {
        LoginSession session = authService.requireLogin(sessionId);
        if (name == null || name.isBlank()) {
            throw new BusinessException("이름을 입력해 주세요.");
        }
        int rows = userDAO.updateName(session.getUserId(), name);
        if (rows == 0) {
            throw new BusinessException("이름을 변경할 수 없습니다.");
        }
    };

    public void withdraw(String sessionId) throws SQLException {
        LoginSession session = authService.requireLogin(sessionId);
        boolean withdrawn = userDAO.withdrawIfPossible(session.getUserId());
        if (!withdrawn) {
            throw  new BusinessException("확정된 예약이 있어 탈퇴할 수 없습니다.");
        }
        authService.logout(sessionId);
    };

    public List<User> getAllUsers(String sessionId) throws SQLException {
        LoginSession session = authService.requireLogin(sessionId);

        if (session.getRole() != UserRole.ADMIN) {
            throw new BusinessException("관리자만 가능한 기능입니다");
        }
        return userDAO.findAll();

    };
}
