package com.studyroom.reservation.dto;

import com.studyroom.reservation.enums.RefundStatus;

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
public class Refund {

    private Long refundId;
    private Long reservationId;
    private RefundStatus status;
}
