package com.serviceportal.bff.dto;

import lombok.Data;

@Data
public class LoginResponse {
    private String token;
    private String type;
}
