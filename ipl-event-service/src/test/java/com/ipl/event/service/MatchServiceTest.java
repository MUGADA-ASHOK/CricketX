package com.ipl.event.service;

import com.ipl.event.dto.match.CreateMatchRequest;
import com.ipl.event.dto.match.MatchResponse;
import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Match;
import com.ipl.event.entity.Stadium;
import com.ipl.event.enums.MatchStatus;
import com.ipl.event.exception.InvalidMatchStateException;
import com.ipl.event.kafka.OutboxPublisherService;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.MatchRepository;
import com.ipl.event.repository.MatchTicketInventoryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MatchServiceTest {

    @Mock
    private MatchRepository matchRepository;

    @Mock
    private FranchiseRepository franchiseRepository;

    @Mock
    private MatchTicketInventoryRepository matchTicketInventoryRepository;

    @Mock
    private OutboxPublisherService outboxPublisherService;

    @InjectMocks
    private MatchService matchService;

    private Franchise homeTeam;
    private Franchise awayTeam;

    @BeforeEach
    void setUp() {
        homeTeam = Franchise.builder().id(1L).franchiseName("Chennai Super Kings").teamCode("CSK").build();
        awayTeam = Franchise.builder().id(2L).franchiseName("Royal Challengers Bengaluru").teamCode("RCB").build();
    }

    @Test
    @DisplayName("Should successfully schedule match in CONFIGURATION_PENDING state and enqueue outbox event")
    void testCreateMatch_Success() {
        CreateMatchRequest request = new CreateMatchRequest();
        request.setHomeFranchiseId(1L);
        request.setAwayFranchiseId(2L);
        request.setMatchDateTime(LocalDateTime.now().plusDays(20));

        when(franchiseRepository.findById(1L)).thenReturn(Optional.of(homeTeam));
        when(franchiseRepository.findById(2L)).thenReturn(Optional.of(awayTeam));
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> {
            Match m = inv.getArgument(0);
            m.setId(10L);
            return m;
        });

        MatchResponse response = matchService.createMatch(request);

        assertThat(response).isNotNull();
        assertThat(response.getMatchId()).isEqualTo(10L);
        assertThat(response.getStatus()).isEqualTo(MatchStatus.CONFIGURATION_PENDING);
        verify(outboxPublisherService).saveOutboxEvent(eq("MATCH"), eq(10L), eq("MatchScheduledEvent"), anyString(), any());
    }

    @Test
    @DisplayName("Should reject scheduling when home and away franchise IDs are identical")
    void testCreateMatch_SameHomeAndAway_Throws() {
        CreateMatchRequest request = new CreateMatchRequest();
        request.setHomeFranchiseId(1L);
        request.setAwayFranchiseId(1L);
        request.setMatchDateTime(LocalDateTime.now().plusDays(20));

        assertThatThrownBy(() -> matchService.createMatch(request))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Home and away franchise must be different");
    }

    @Test
    @DisplayName("Should approve match, compute ticketOpenAt (Match Date - 5 Days), and save outbox event")
    void testApproveMatch_Success() {
        LocalDateTime matchTime = LocalDateTime.of(2027, 4, 15, 19, 30);
        Match match = Match.builder()
                .id(1L)
                .homeFranchise(homeTeam)
                .awayFranchise(awayTeam)
                .stadium(Stadium.builder().id(1L).name("Chepauk").capacity(50000).build())
                .matchDateTime(matchTime)
                .status(MatchStatus.WAITING_FOR_APPROVAL)
                .build();

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));
        when(matchTicketInventoryRepository.existsByMatchId(1L)).thenReturn(true);
        when(matchRepository.save(any(Match.class))).thenAnswer(inv -> inv.getArgument(0));

        MatchResponse response = matchService.approveMatch(1L);

        assertThat(response.getStatus()).isEqualTo(MatchStatus.APPROVED);
        assertThat(match.getTicketOpenAt()).isEqualTo(matchTime.minusDays(5));
        verify(outboxPublisherService).saveOutboxEvent(eq("MATCH"), eq(1L), eq("MatchApprovedEvent"), anyString(), any());
    }

    @Test
    @DisplayName("Should return existing match without creating duplicate outbox event when re-approving (Idempotent)")
    void testApproveMatch_Idempotent_AlreadyApproved() {
        Match match = Match.builder()
                .id(1L)
                .homeFranchise(homeTeam)
                .awayFranchise(awayTeam)
                .status(MatchStatus.APPROVED)
                .build();

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        MatchResponse response = matchService.approveMatch(1L);

        assertThat(response.getStatus()).isEqualTo(MatchStatus.APPROVED);
        verifyNoInteractions(outboxPublisherService);
        verify(matchRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should reject approval when match is not in WAITING_FOR_APPROVAL status")
    void testApproveMatch_InvalidState_ThrowsException() {
        Match match = Match.builder()
                .id(1L)
                .homeFranchise(homeTeam)
                .awayFranchise(awayTeam)
                .status(MatchStatus.CONFIGURATION_PENDING)
                .build();

        when(matchRepository.findById(1L)).thenReturn(Optional.of(match));

        assertThatThrownBy(() -> matchService.approveMatch(1L))
                .isInstanceOf(InvalidMatchStateException.class)
                .hasMessageContaining("Match must be WAITING_FOR_APPROVAL to approve");
    }
}

