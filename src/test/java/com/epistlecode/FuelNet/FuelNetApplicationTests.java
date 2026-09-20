package com.epistlecode.FuelNet;

import com.epistlecode.FuelNet.model.UserRole;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class FuelNetApplicationTests {

    @Autowired UserRepository userRepository;
    @Autowired StationRepository stationRepository;
    @Autowired FuelPriceRepository fuelPriceRepository;

    @Test
    void contextLoads() {
    }

    @Test
    void seedDataIsCreatedOnStartup() {
        var admin = userRepository.findByEmail("admin@fuelnet.com");
        assertThat(admin).isNotNull();
        assertThat(admin.getRole()).isEqualTo(UserRole.ROLE_ADMIN);

        assertThat(stationRepository.count()).isGreaterThan(0);
        // every seeded station gets an opening price for each of the 3 fuel types
        assertThat(fuelPriceRepository.findCurrentPrices()).hasSize((int) stationRepository.count() * 3);
        assertThat(fuelPriceRepository.count()).isEqualTo(stationRepository.count() * 3 * 4);
    }
}
