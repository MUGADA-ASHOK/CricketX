package com.ipl.event.dto.franchise;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class CreateFranchiseRequest {

    @NotBlank
    @Size(max = 100)
    private String franchiseName;

    @NotBlank
    @Size(max = 10)
    private String teamCode;

    @Size(max = 50)
    private String shortName;

    @NotBlank
    @Size(max = 100)
    private String city;

    @Size(max = 500)
    private String logoUrl;

    private String description;

    private Long homeStadiumId;

    @Email
    @Size(max = 150)
    private String contactEmail;

    @Size(max = 20)
    private String contactPhone;
}