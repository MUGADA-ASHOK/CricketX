package com.ipl.event.dto.franchise;

import jakarta.validation.constraints.Email;
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
public class CreateFranchiseRequest {

    @NotNull(message = "User ID of the franchise account is required")
    private Long userId;

    @NotBlank(message = "Franchise name is required")
    private String franchiseName;

    @NotBlank(message = "Team code is required (e.g. CSK, RCB)")
    private String teamCode;

    private String shortName;

    private String city;

    private String description;

    private String logoUrl;

    @Email(message = "Valid contact email is required")
    private String contactEmail;

    private String contactPhone;

    private Long homeStadiumId;
}