package com.qwikbrew.userservice.dto;

import lombok.*;

@Getter @Setter @NoArgsConstructor @AllArgsConstructor
public class PointsResponse {
    private Integer points;
    private String  tier;
}
