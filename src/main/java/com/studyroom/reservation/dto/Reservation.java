package com.studyroom.reservation.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import com.studyroom.reservation.enums.ReservationStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Reservation {

    private Long reservationId;
    private Long userId;
    private Long roomId;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private BigDecimal totalPrice;
    private ReservationStatus status;
}
