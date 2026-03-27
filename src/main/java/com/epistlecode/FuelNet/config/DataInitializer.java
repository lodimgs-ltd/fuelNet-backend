package com.epistlecode.FuelNet.config;

import com.epistlecode.FuelNet.model.*;
import com.epistlecode.FuelNet.respository.FuelPriceRepository;
import com.epistlecode.FuelNet.respository.FuelTypeRepository;
import com.epistlecode.FuelNet.respository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.sql.Timestamp;

@Configuration
public class DataInitializer {

    private final FuelTypeRepository fuelTypeRepository;
    private final FuelPriceRepository fuelPriceRepository;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataInitializer(
            FuelTypeRepository fuelTypeRepository,
            FuelPriceRepository fuelPriceRepository,
            UserRepository userRepository,
            PasswordEncoder passwordEncoder
    ) {
        this.fuelTypeRepository = fuelTypeRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Bean
    public CommandLineRunner initData() {
        return args -> {

            // 1️⃣ Create Admin User
            User admin = createAdminIfNotExists();

            // 2️⃣ Create Fuel Types
            FuelType pms = createFuelTypeIfNotExists("PMS");
            FuelType diesel = createFuelTypeIfNotExists("Diesel");
            FuelType kerosene = createFuelTypeIfNotExists("Kerosene");

            // 3️⃣ Create Initial Fuel Prices
            createFuelPriceIfNotExists(pms, 500.0, admin);
            createFuelPriceIfNotExists(diesel, 700.0, admin);
            createFuelPriceIfNotExists(kerosene, 650.0, admin);
        };
    }

    // ===================== HELPERS =====================

    private User createAdminIfNotExists() {
        String email = "admin@fuelnet.com";

        User existing = userRepository.findByEmail(email);
        if (existing != null) {
            return existing;
        }

        User admin = new User();
        admin.setFullName("System Admin");
        admin.setEmail(email);
        admin.setPassword(passwordEncoder.encode("admin123")); // change later
        admin.setRole(UserRole.ROLE_ADMIN);
        admin.setStatus(UserStatus.ENABLED);
        admin.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        userRepository.save(admin);
        System.out.println("✅ Created default admin user");

        return admin;
    }

    private FuelType createFuelTypeIfNotExists(String name) {
        FuelType fuelType = fuelTypeRepository.findByName(name);

        if (fuelType == null) {
            fuelType = new FuelType();
            fuelType.setName(name);
            fuelType.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            fuelTypeRepository.save(fuelType);
            System.out.println("✅ Created fuel type: " + name);
        }

        return fuelType;
    }

    private void createFuelPriceIfNotExists(FuelType fuelType, double price, User admin) {
        FuelPrice existingPrice = fuelPriceRepository.findByFuelType(fuelType);

        if (existingPrice != null) {
            return;
        }

        FuelPrice fuelPrice = new FuelPrice();
        fuelPrice.setFuelType(fuelType);
        fuelPrice.setPrice(price);
        fuelPrice.setSetBy(admin);
        fuelPrice.setCreatedAt(new Timestamp(System.currentTimeMillis()));

        fuelPriceRepository.save(fuelPrice);
        System.out.println("✅ Created price for " + fuelType.getName());
    }
}

