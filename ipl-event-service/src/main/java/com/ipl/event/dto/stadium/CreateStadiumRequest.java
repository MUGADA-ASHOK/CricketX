package com.ipl.event.dto.stadium;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateStadiumRequest {

    @NotBlank(message = "Stadium name is required")
    private String name;

    @NotBlank(message = "City is required")
    private String city;

    private String state;

    private String country;

    private String address;

    @NotNull(message = "Capacity is required")
    @Min(value = 1000, message = "Capacity must be at least 1,000")
    private Integer capacity;

    private String description;

    private String imageUrl;
}
