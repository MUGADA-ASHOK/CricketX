package com.ipl.event.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Event Data Seeder.
 * Pre-seeded mock data has been disabled so that all Stadiums, Franchises,
 * and Match Configurations are created manually through the frontend governance UI.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class EventDataSeeder implements CommandLineRunner {

    @Override
    public void run(String... args) {
        log.info("Manual management mode active: No static stadiums, franchises, or matches are pre-seeded.");
    }
}
