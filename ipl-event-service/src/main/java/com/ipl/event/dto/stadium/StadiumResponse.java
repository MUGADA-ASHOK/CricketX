package com.ipl.event.dto.stadium;

import com.ipl.event.enums.StadiumStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class StadiumResponse {

    private Long id;

    private String name;

    private String city;

    private String state;

    private String country;

    private String address;

    private Integer capacity;

    private String description;

    private String imageUrl;

    private StadiumStatus status;
}