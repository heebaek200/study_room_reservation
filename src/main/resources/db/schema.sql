CREATE DATABASE study_room_reservation;

USE study_room_reservation;

CREATE TABLE users (
    user_id       BIGINT UNSIGNED             NOT NULL AUTO_INCREMENT COMMENT '회원 고유번호'
  , email         VARCHAR(255)                NOT NULL                COMMENT '로그인 이메일'
  , password_hash VARCHAR(255)                NOT NULL                COMMENT '단방향 비밀번호 해시값'
  , name          VARCHAR(50)                 NOT NULL                COMMENT '회원 이름'
  , role          ENUM('USER', 'ADMIN')       NOT NULL DEFAULT 'USER' COMMENT '회원 권한'
  , status        ENUM('ACTIVE', 'WITHDRAWN') NOT NULL DEFAULT 'ACTIVE' COMMENT '회원 상태'
  , CONSTRAINT pk_users PRIMARY KEY (user_id)
  , CONSTRAINT uk_users_email UNIQUE (email)
) COMMENT = '회원';

CREATE TABLE study_room (
    room_id     BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '스터디룸 고유번호'
  , name        VARCHAR(100)    NOT NULL                COMMENT '스터디룸 이름'
  , capacity    INT UNSIGNED    NOT NULL                COMMENT '최대 수용 인원'
  , hourly_rate DECIMAL(10, 2)  NOT NULL                COMMENT '시간당 이용료'
  , CONSTRAINT pk_study_room PRIMARY KEY (room_id)
  , CONSTRAINT uk_study_room_name UNIQUE (name)
  , CONSTRAINT chk_study_room_capacity CHECK (capacity > 0)
  , CONSTRAINT chk_study_room_hourly_rate CHECK (hourly_rate >= 0)
) COMMENT = '스터디룸';

CREATE TABLE reservation (
    reservation_id BIGINT UNSIGNED                NOT NULL AUTO_INCREMENT COMMENT '예약 고유번호'
  , user_id         BIGINT UNSIGNED                NOT NULL                COMMENT '예약 회원 고유번호'
  , room_id         BIGINT UNSIGNED                NOT NULL                COMMENT '스터디룸 고유번호'
  , start_time      DATETIME                       NOT NULL                COMMENT '예약 시작 일시'
  , end_time        DATETIME                       NOT NULL                COMMENT '예약 종료 일시'
  , total_price     DECIMAL(10, 2)                 NOT NULL                COMMENT '예약 당시 총 이용 금액'
  , status          ENUM('CONFIRMED', 'CANCELLED') NOT NULL DEFAULT 'CONFIRMED' COMMENT '예약 상태'
  , CONSTRAINT pk_reservation PRIMARY KEY (reservation_id)
  , CONSTRAINT fk_reservation_user FOREIGN KEY (user_id)
        REFERENCES users (user_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
  , CONSTRAINT fk_reservation_room FOREIGN KEY (room_id)
        REFERENCES study_room (room_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
  , CONSTRAINT chk_reservation_time CHECK (end_time > start_time)
  , CONSTRAINT chk_reservation_total_price CHECK (total_price >= 0)
  , INDEX idx_reservation_user (user_id)
  , INDEX idx_reservation_room_time (room_id, status, start_time, end_time)
) COMMENT = '예약';

CREATE TABLE refund (
    refund_id      BIGINT UNSIGNED                           NOT NULL AUTO_INCREMENT COMMENT '환불 요청 고유번호'
  , reservation_id BIGINT UNSIGNED                           NOT NULL                COMMENT '예약 고유번호'
  , status          ENUM('REQUESTED', 'APPROVED', 'REJECTED') NOT NULL DEFAULT 'REQUESTED' COMMENT '환불 처리 상태'
  , CONSTRAINT pk_refund PRIMARY KEY (refund_id)
  , CONSTRAINT uk_refund_reservation UNIQUE (reservation_id)
  , CONSTRAINT fk_refund_reservation FOREIGN KEY (reservation_id)
        REFERENCES reservation (reservation_id)
        ON UPDATE RESTRICT
        ON DELETE RESTRICT
  , INDEX idx_refund_status (status)
) COMMENT = '환불 요청';
