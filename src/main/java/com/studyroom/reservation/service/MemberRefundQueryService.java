package com.studyroom.reservation.service;

import com.studyroom.reservation.dao.MemberRefundQueryDAO;
import com.studyroom.reservation.dto.Refund;
import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.exception.BusinessException;
import com.studyroom.reservation.session.LoginSession;

import java.sql.SQLException;
import java.util.List;

/**
 * 일반 회원의 본인 환불 내역 조회를 처리합니다.
 */
public class MemberRefundQueryService {

    private final AuthService authService;
    private final MemberRefundQueryDAO memberRefundQueryDAO;

    /**
     * 환불 조회에 필요한 인증 서비스와 DAO를 전달받습니다.
     *
     * @param authService 로그인 세션 확인에 사용할 서비스
     * @param memberRefundQueryDAO 회원 환불 조회 DAO
     */
    public MemberRefundQueryService(
            AuthService authService,
            MemberRefundQueryDAO memberRefundQueryDAO
    ) {
        this.authService = authService;
        this.memberRefundQueryDAO = memberRefundQueryDAO;
    }

    /**
     * 현재 로그인한 일반 회원의 환불 내역을 조회합니다.
     *
     * userId를 외부에서 전달받지 않고,
     * 로그인 세션에서 직접 가져와 본인 데이터만 조회합니다.
     *
     * @param sessionId 로그인 세션 ID
     * @return 본인의 환불 목록. 내역이 없으면 빈 목록
     * @throws SQLException DB 조회 중 오류가 발생한 경우
     * @throws BusinessException 로그인하지 않았거나 일반 회원이 아닌 경우
     */
    public List<Refund> getMyRefunds(String sessionId)
            throws SQLException {

        LoginSession session = authService.requireLogin(sessionId);

        if (session.getRole() != UserRole.USER) {
            throw new BusinessException(
                    "일반 회원만 본인 환불 내역을 조회할 수 있습니다."
            );
        }

        long userId = session.getUserId();

        return memberRefundQueryDAO.findByUserId(userId);
    }
}
