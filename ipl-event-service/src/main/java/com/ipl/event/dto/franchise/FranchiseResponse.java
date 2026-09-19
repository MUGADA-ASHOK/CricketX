package com.ipl.event.dto.franchise;

import com.ipl.event.enums.FranchiseStatus;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class FranchiseResponse {

    private Long id;

    private Long userId;

    private String franchiseName;

    private String teamCode;

    private String shortName;

    private String city;

    private String logoUrl;

    private String description;

    private Long homeStadiumId;

    private String contactEmail;

    private String contactPhone;

    private FranchiseStatus status;
}
