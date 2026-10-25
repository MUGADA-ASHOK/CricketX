package com.ipl.event.dto.franchise;

import com.ipl.event.entity.Franchise;
import com.ipl.event.enums.FranchiseStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FranchiseResponse {

    private Long id;
    private Long userId;
    private String franchiseName;
    private String teamCode;
    private String shortName;
    private String city;
    private String description;
    private String logoUrl;
    private String contactEmail;
    private String contactPhone;
    private Long homeStadiumId;
    private String homeStadiumName;
    private FranchiseStatus status;

    public static FranchiseResponse fromEntity(Franchise franchise) {
        return FranchiseResponse.builder()
                .id(franchise.getId())
                .userId(franchise.getUserId())
                .franchiseName(franchise.getFranchiseName())
                .teamCode(franchise.getTeamCode())
                .shortName(franchise.getShortName())
                .city(franchise.getCity())
                .description(franchise.getDescription())
                .logoUrl(franchise.getLogoUrl())
                .contactEmail(franchise.getContactEmail())
                .contactPhone(franchise.getContactPhone())
                .homeStadiumId(franchise.getHomeStadium() != null ? franchise.getHomeStadium().getId() : null)
                .homeStadiumName(franchise.getHomeStadium() != null ? franchise.getHomeStadium().getName() : null)
                .status(franchise.getStatus())
                .build();
    }
}
