package com.ipl.event.config;

import com.ipl.event.entity.Franchise;
import com.ipl.event.entity.Stadium;
import com.ipl.event.entity.TicketCategory;
import com.ipl.event.enums.FranchiseStatus;
import com.ipl.event.enums.StadiumStatus;
import com.ipl.event.enums.TicketCategoryStatus;
import com.ipl.event.repository.FranchiseRepository;
import com.ipl.event.repository.StadiumRepository;
import com.ipl.event.repository.TicketCategoryRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds initial Franchises, Stadiums, and Ticket Categories on startup
 * if not already present, enabling immediate end-to-end testing without manual SQL.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventDataSeeder implements CommandLineRunner {

    private final FranchiseRepository franchiseRepository;
    private final StadiumRepository stadiumRepository;
    private final TicketCategoryRepository ticketCategoryRepository;

    @Override
    public void run(String... args) {
        seedStadiumAndCategories();
        seedFranchises();
    }

    private void seedStadiumAndCategories() {
        if (stadiumRepository.count() == 0) {
            log.info("Seeding initial Stadium and Ticket Categories...");
            Stadium stadium = Stadium.builder()
                    .name("M. Chinnaswamy Stadium")
                    .city("Bengaluru")
                    .state("Karnataka")
                    .country("India")
                    .address("Cubbon Park, Bengaluru")
                    .capacity(40000)
                    .status(StadiumStatus.ACTIVE)
                    .build();
            Stadium savedStadium = stadiumRepository.save(stadium);

            TicketCategory vip = TicketCategory.builder()
                    .stadium(savedStadium)
                    .categoryName("VIP Lounge")
                    .description("Air-conditioned luxury seating with catering")
                    .price(BigDecimal.valueOf(15000.00))
                    .capacity(500)
                    .availableQuantity(500)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build();

            TicketCategory premium = TicketCategory.builder()
                    .stadium(savedStadium)
                    .categoryName("Premium Pavilion")
                    .description("Covered upper tier with prime pitch view")
                    .price(BigDecimal.valueOf(5000.00))
                    .capacity(2500)
                    .availableQuantity(2500)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build();

            TicketCategory general = TicketCategory.builder()
                    .stadium(savedStadium)
                    .categoryName("General Stand")
                    .description("Open grandstand seats")
                    .price(BigDecimal.valueOf(1500.00))
                    .capacity(25000)
                    .availableQuantity(25000)
                    .status(TicketCategoryStatus.ACTIVE)
                    .build();

            ticketCategoryRepository.save(vip);
            ticketCategoryRepository.save(premium);
            ticketCategoryRepository.save(general);
            log.info("Stadium and categories seeded successfully");
        }
    }

    private void seedFranchises() {
        if (franchiseRepository.count() == 0) {
            log.info("Seeding initial IPL Franchises...");
            Stadium stadium = stadiumRepository.findAll().stream().findFirst().orElse(null);

            Franchise rcb = Franchise.builder()
                    .userId(2L) // Maps to BCCI Admin / Franchise account in Auth Service
                    .franchiseName("Royal Challengers Bengaluru")
                    .teamCode("RCB")
                    .shortName("Bengaluru")
                    .city("Bengaluru")
                    .description("Play Bold - Royal Challengers Bengaluru IPL Team")
                    .logoUrl("https://upload.wikimedia.org/wikipedia/en/thumb/2/2a/Royal_Challengers_Bangalore_2020.svg/200px-Royal_Challengers_Bangalore_2020.svg.png")
                    .contactEmail("contact@royalchallengers.com")
                    .contactPhone("+91-8022211111")
                    .homeStadium(stadium)
                    .status(FranchiseStatus.ACTIVE)
                    .build();

            Franchise csk = Franchise.builder()
                    .userId(99L)
                    .franchiseName("Chennai Super Kings")
                    .teamCode("CSK")
                    .shortName("Chennai")
                    .city("Chennai")
                    .description("Whistle Podu - 5-time IPL Champions")
                    .logoUrl("https://upload.wikimedia.org/wikipedia/en/thumb/2/2b/Chennai_Super_Kings_Logo.svg/200px-Chennai_Super_Kings_Logo.svg.png")
                    .contactEmail("support@chennaisuperkings.com")
                    .contactPhone("+91-4422233333")
                    .homeStadium(stadium)
                    .status(FranchiseStatus.ACTIVE)
                    .build();

            Franchise mi = Franchise.builder()
                    .userId(98L)
                    .franchiseName("Mumbai Indians")
                    .teamCode("MI")
                    .shortName("Mumbai")
                    .city("Mumbai")
                    .description("Duniya Hila Denge Hum - 5-time IPL Champions")
                    .logoUrl("https://upload.wikimedia.org/wikipedia/en/thumb/c/cd/Mumbai_Indians_Logo.svg/200px-Mumbai_Indians_Logo.svg.png")
                    .contactEmail("info@mumbaiindians.com")
                    .contactPhone("+91-2224445555")
                    .homeStadium(stadium)
                    .status(FranchiseStatus.ACTIVE)
                    .build();

            franchiseRepository.save(rcb);
            franchiseRepository.save(csk);
            franchiseRepository.save(mi);
            log.info("Franchises seeded successfully with full details");
        }
    }
}
