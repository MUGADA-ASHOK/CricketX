package com.ipl.event.dto.franchise;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateFranchiseRequest {

    private String franchiseName;
    private String shortName;
    private String city;
    private String description;
    private String logoUrl;
    private String contactEmail;
    private String contactPhone;
    private Long homeStadiumId;
}

