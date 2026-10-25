package com.ipl.event.service;

import com.ipl.event.dto.stadium.CreateStadiumRequest;
import com.ipl.event.dto.stadium.StadiumResponse;
import com.ipl.event.entity.Stadium;
import com.ipl.event.entity.TicketCategory;
import com.ipl.event.enums.StadiumStatus;
import com.ipl.event.enums.TicketCategoryStatus;
import com.ipl.event.exception.StadiumNotFoundException;
import com.ipl.event.repository.StadiumRepository;
import com.ipl.event.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class StadiumService {

    private final StadiumRepository stadiumRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    @Transactional
    public StadiumResponse createStadium(CreateStadiumRequest request) {
        if (stadiumRepository.existsByNameIgnoreCase(request.getName())) {
            throw new IllegalArgumentException("A stadium with the name '" + request.getName() + "' already exists");
        }

        Stadium stadium = Stadium.builder()
                .name(request.getName())
                .city(request.getCity())
                .state(request.getState())
                .country(request.getCountry() != null ? request.getCountry() : "India")
                .address(request.getAddress())
                .capacity(request.getCapacity())
                .description(request.getDescription())
                .imageUrl(request.getImageUrl())
                .status(StadiumStatus.ACTIVE)
                .build();

        Stadium saved = stadiumRepository.save(stadium);

        // Pre-create baseline standard ticket tiers for this stadium
        seedDefaultCategories(saved);

        log.info("Created new stadium: {} (ID: {})", saved.getName(), saved.getId());
        return StadiumResponse.fromEntity(saved);
    }

    @Transactional(readOnly = true)
    public List<StadiumResponse> listStadiums() {
        return stadiumRepository.findAll().stream()
                .map(StadiumResponse::fromEntity)
                .toList();
    }

    @Transactional(readOnly = true)
    public StadiumResponse getStadium(Long id) {
        Stadium stadium = stadiumRepository.findById(id)
                .orElseThrow(() -> new StadiumNotFoundException("Stadium not found: " + id));
        return StadiumResponse.fromEntity(stadium);
    }

    private void seedDefaultCategories(Stadium stadium) {
        int cap = stadium.getCapacity();
        int vipCap = Math.max(100, (int) (cap * 0.05));
        int premCap = Math.max(500, (int) (cap * 0.20));
        int genCap = cap - vipCap - premCap;

        TicketCategory vip = TicketCategory.builder()
                .stadium(stadium)
                .categoryName("VIP Lounge")
                .description("Luxury hospitality box seating with full catering")
                .price(BigDecimal.valueOf(15000.00))
                .capacity(vipCap)
                .availableQuantity(vipCap)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        TicketCategory prem = TicketCategory.builder()
                .stadium(stadium)
                .categoryName("Premium Pavilion")
                .description("Reserved grandstand seating with prime boundary views")
                .price(BigDecimal.valueOf(5000.00))
                .capacity(premCap)
                .availableQuantity(premCap)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        TicketCategory gen = TicketCategory.builder()
                .stadium(stadium)
                .categoryName("General Stand")
                .description("Open public tier grandstand seating")
                .price(BigDecimal.valueOf(1500.00))
                .capacity(genCap)
                .availableQuantity(genCap)
                .status(TicketCategoryStatus.ACTIVE)
                .build();

        ticketCategoryRepository.saveAll(List.of(vip, prem, gen));
    }
}
