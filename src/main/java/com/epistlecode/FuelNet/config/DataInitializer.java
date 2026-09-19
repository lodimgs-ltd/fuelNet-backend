package com.epistlecode.FuelNet.config;

import com.epistlecode.FuelNet.model.*;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.FuelTypeRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;
import java.util.List;

/**
 * Seeds the minimum data the app needs to be usable on a fresh database:
 * one admin, the standard fuel types, a handful of stations and an opening
 * price for each. Every step is idempotent so restarts are safe.
 */
@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    private final FuelTypeRepository fuelTypeRepository;
    private final FuelPriceRepository fuelPriceRepository;
    private final StationRepository stationRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final String adminEmail;
    private final String adminPassword;

    public DataInitializer(FuelTypeRepository fuelTypeRepository,
                           FuelPriceRepository fuelPriceRepository,
                           StationRepository stationRepository,
                           UserRepository userRepository,
                           PasswordEncoder passwordEncoder,
                           @Value("${app.seed.admin-email}") String adminEmail,
                           @Value("${app.seed.admin-password}") String adminPassword) {
        this.fuelTypeRepository = fuelTypeRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.stationRepository = stationRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.adminEmail = adminEmail;
        this.adminPassword = adminPassword;
    }

    private record SeedStation(String name, String address, String city, String state, double lat, double lng,
                               double pms, double diesel, double kerosene) {}

    private static final List<SeedStation> SEED_STATIONS = List.of(
            new SeedStation("FuelNet Ugbowo", "Ugbowo-Lagos Road, opposite UNIBEN main gate", "Benin City", "Edo",
                    6.3990, 5.6100, 865, 1050, 1120),
            new SeedStation("FuelNet Sapele Road", "Sapele Road, by Aduwawa junction", "Benin City", "Edo",
                    6.3240, 5.6420, 870, 1045, 1125),
            new SeedStation("FuelNet Airport Road", "Airport Road, GRA", "Benin City", "Edo",
                    6.3170, 5.6000, 860, 1040, 1115),
            new SeedStation("FuelNet Ikeja", "Obafemi Awolowo Way, Ikeja", "Lagos", "Lagos",
                    6.6018, 3.3515, 855, 1035, 1110)
    );

    @Bean
    public CommandLineRunner initData() {
        return args -> {
            User admin = createAdminIfNotExists();

            FuelType pms = createFuelTypeIfNotExists("PMS");
            FuelType diesel = createFuelTypeIfNotExists("Diesel");
            FuelType kerosene = createFuelTypeIfNotExists("Kerosene");

            if (stationRepository.count() == 0) {
                for (SeedStation seed : SEED_STATIONS) {
                    Station station = createStation(seed);
                    createOpeningPrice(station, pms, seed.pms(), admin);
                    createOpeningPrice(station, diesel, seed.diesel(), admin);
                    createOpeningPrice(station, kerosene, seed.kerosene(), admin);
                }
                log.info("Seeded {} stations with opening prices", SEED_STATIONS.size());
            }

            backfillLegacyPrices();
        };
    }

    private User createAdminIfNotExists() {
        User existing = userRepository.findByEmail(adminEmail);
        if (existing != null) {
            return existing;
        }
        User admin = new User();
        admin.setFullName("System Admin");
        admin.setEmail(adminEmail);
        admin.setPassword(passwordEncoder.encode(adminPassword));
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setStatus(UserStatus.ENABLED);
        admin.setCreatedAt(now());
        userRepository.save(admin);
        log.info("Created default admin user {}", adminEmail);
        return admin;
    }

    private FuelType createFuelTypeIfNotExists(String name) {
        FuelType fuelType = fuelTypeRepository.findByName(name);
        if (fuelType == null) {
            fuelType = new FuelType();
            fuelType.setName(name);
            fuelType.setCreatedAt(now());
            fuelTypeRepository.save(fuelType);
            log.info("Created fuel type {}", name);
        }
        return fuelType;
    }

    private Station createStation(SeedStation seed) {
        Station station = new Station();
        station.setName(seed.name());
        station.setAddress(seed.address());
        station.setCity(seed.city());
        station.setState(seed.state());
        station.setLatitude(seed.lat());
        station.setLongitude(seed.lng());
        station.setStatus(StationStatus.ACTIVE);
        station.setCreatedAt(now());
        return stationRepository.save(station);
    }

    private void createOpeningPrice(Station station, FuelType fuelType, double price, User admin) {
        FuelPrice fuelPrice = new FuelPrice();
        fuelPrice.setStation(station);
        fuelPrice.setFuelType(fuelType);
        fuelPrice.setPrice(price);
        fuelPrice.setPreviousPrice(null);
        fuelPrice.setSetBy(admin);
        fuelPrice.setCreatedAt(now());
        fuelPriceRepository.save(fuelPrice);
    }

    /**
     * Rows created before stations existed have no station. Attach them to the
     * first station so they still show up in history instead of being orphaned.
     */
    private void backfillLegacyPrices() {
        List<FuelPrice> orphans = fuelPriceRepository.findAll().stream()
                .filter(p -> p.getStation() == null)
                .toList();
        if (orphans.isEmpty()) {
            return;
        }
        Station first = stationRepository.findAllByOrderByNameAsc().stream().findFirst().orElse(null);
        if (first == null) {
            return;
        }
        orphans.forEach(p -> p.setStation(first));
        fuelPriceRepository.saveAll(orphans);
        log.info("Attached {} legacy price rows to station '{}'", orphans.size(), first.getName());
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
