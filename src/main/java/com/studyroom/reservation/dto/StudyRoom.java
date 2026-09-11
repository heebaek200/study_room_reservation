package com.studyroom.reservation.dto;

import java.math.BigDecimal;

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
public class StudyRoom {

    private Long roomId;
    private String name;
    private Integer capacity;
    private BigDecimal hourlyRate;
}
