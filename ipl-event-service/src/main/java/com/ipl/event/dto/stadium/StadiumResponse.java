package com.ipl.event.dto.stadium;

import com.ipl.event.entity.Stadium;
import com.ipl.event.enums.StadiumStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
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

    public static StadiumResponse fromEntity(Stadium stadium) {
        return StadiumResponse.builder()
                .id(stadium.getId())
                .name(stadium.getName())
                .city(stadium.getCity())
                .state(stadium.getState())
                .country(stadium.getCountry())
                .address(stadium.getAddress())
                .capacity(stadium.getCapacity())
                .description(stadium.getDescription())
                .imageUrl(stadium.getImageUrl())
                .status(stadium.getStatus())
                .build();
    }
}