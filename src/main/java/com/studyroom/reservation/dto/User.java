package com.studyroom.reservation.dto;

import com.studyroom.reservation.enums.UserRole;
import com.studyroom.reservation.enums.UserStatus;

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
public class User {

    private Long userId;
    private String email;
    private String passwordHash;
    private String name;
    private UserRole role;
    private UserStatus status;
}
