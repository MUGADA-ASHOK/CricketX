package com.ipl.event.service;

import com.ipl.event.dto.match.MatchConfigurationRequest;
import com.ipl.event.dto.match.MatchConfigurationResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Match;
import com.ipl.event.entity.Stadium;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.exception.InvalidTicketConfigurationException;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.MatchRepository;
import com.ipl.event.repository.MatchTicketInventoryRepository;
import com.ipl.event.repository.StadiumRepository;
import com.ipl.event.security.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MatchConfigurationServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FranchiseRepository franchiseRepository;

    @Mock
    private StadiumRepository stadiumRepository;

    @Mock
    private MatchTicketInventoryRepository matchTicketInventoryRepository;

    @InjectMocks
    private MatchConfigurationService matchConfigurationService;

    private AuthenticatedUser cskAdmin;
    private Franchise cskFranchise;
    private Stadium stadium;
    private Match match;

    @BeforeEach
    void setUp() {
        cskAdmin = new AuthenticatedUser(5L, "admin@csk.com", "ROLE_FRANCHISE");
        cskFranchise = Franchise.builder().id(1L).userId(5L).franchiseName("CSK").build();
        stadium = Stadium.builder().id(1L).name("Chepauk").capacity(50000).build();

        match = Match.builder()
                .id(1L)
                .homeFranchise(cskFranchise)
                .status(MatchStatus.CONFIGURATION_PENDING)
                .build();
    }

    @Test
    @DisplayName("Should reject configuration when total category seats exceed stadium capacity")
    void testConfigureMatch_CapacityExceeded_ThrowsException() {
        MatchConfigurationRequest request = new MatchConfigurationRequest();
        request.setStadiumId(1L);
        // Total requested: 500 + 2500 + 55000 = 58,000 > stadium capacity 50,000
        request.setCategories(List.of(
                new MatchConfigurationRequest.TicketCategoryConfig("VIP Lounge", 500, BigDecimal.valueOf(15000)),
                new MatchConfigurationRequest.TicketCategoryConfig("Premium Pavilion", 2500, BigDecimal.valueOf(5000)),
                new MatchConfigurationRequest.TicketCategoryConfig("General Stand", 55000, BigDecimal.valueOf(1500))
        ));

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(franchiseRepository.findByUserId(5L)).thenReturn(Optional.of(cskFranchise));
        when(matchTicketInventoryRepository.existsByMatchId(1L)).thenReturn(false);
        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));

        assertThatThrownBy(() -> matchConfigurationService.createConfiguration(1L, cskAdmin, request))
                .isInstanceOf(InvalidTicketConfigurationException.class)
                .hasMessageContaining("exceeds stadium capacity");
    }

    @Test
    @DisplayName("Should successfully apply configuration and transition match status to WAITING_FOR_APPROVAL")
    void testConfigureMatch_Success() {
        MatchConfigurationRequest request = new MatchConfigurationRequest();
        request.setStadiumId(1L);
        // Total requested: 500 + 2500 + 25000 = 28,000 <= stadium capacity 50,000
        request.setCategories(List.of(
                new MatchConfigurationRequest.TicketCategoryConfig("VIP Lounge", 500, BigDecimal.valueOf(15000)),
                new MatchConfigurationRequest.TicketCategoryConfig("Premium Pavilion", 2500, BigDecimal.valueOf(5000)),
                new MatchConfigurationRequest.TicketCategoryConfig("General Stand", 25000, BigDecimal.valueOf(1500))
        ));

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(franchiseRepository.findByUserId(5L)).thenReturn(Optional.of(cskFranchise));
        when(matchTicketInventoryRepository.existsByMatchId(1L)).thenReturn(false);
        when(stadiumRepository.findById(1L)).thenReturn(Optional.of(stadium));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchConfigurationResponse response = matchConfigurationService.createConfiguration(1L, cskAdmin, request);

        assertThat(match.getStatus()).isEqualTo(MatchStatus.WAITING_FOR_APPROVAL);
        assertThat(response.getStatus()).isEqualTo(MatchStatus.WAITING_FOR_APPROVAL);
    }
}

