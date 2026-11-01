package com.ipl.order.dto.response;

import com.ipl.order.entity.MatchSeat;
import com.ipl.order.enums.SeatStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SeatResponse {
    private Long id;
    private Long matchId;
    private Long ticketCategoryId;
    private String categoryName;
    private String stand;
    private String section;
    private String rowName;
    private String seatNumber;
    private BigDecimal price;
    private SeatStatus status;

    public static SeatResponse fromEntity(MatchSeat seat) {
        return SeatResponse.builder()
                .id(seat.getId())
                .matchId(seat.getMatchId())
                .ticketCategoryId(seat.getTicketCategoryId())
                .categoryName(seat.getCategoryName())
                .stand(seat.getStand())
                .section(seat.getSection())
                .rowName(seat.getRowName())
                .seatNumber(seat.getSeatNumber())
                .price(seat.getPrice())
                .status(seat.getStatus())
                .build();
    }
}

