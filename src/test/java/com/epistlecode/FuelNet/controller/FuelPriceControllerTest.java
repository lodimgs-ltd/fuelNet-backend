package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.config.SecurityConfig;
import com.epistlecode.FuelNet.dto.FuelPriceResponse;
import com.epistlecode.FuelNet.dto.RecordPriceRequest;
import com.epistlecode.FuelNet.exception.GlobalExceptionHandler;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.service.FuelPriceService;
import com.epistlecode.FuelNet.service.UserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Timestamp;
import java.util.List;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(FuelPriceController.class)
@Import({SecurityConfig.class, JwtProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-fuelnet-unit-tests-32-bytes-min",
        "app.cors.allowed-origins=http://localhost:5173"
})
class FuelPriceControllerTest {

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper objectMapper;
    @Autowired JwtProvider jwtProvider;

    @MockitoBean FuelPriceService fuelPriceService;
    @MockitoBean UserService userService;

    private String tokenFor(String email, String role) {
        return "Bearer " + jwtProvider.generateToken(new UsernamePasswordAuthenticationToken(
                email, null, List.of(new SimpleGrantedAuthority(role))));
    }

    private FuelPriceResponse sample() {
        return new FuelPriceResponse(1L, 1L, "FuelNet Ugbowo", "PMS", 865.0, null, null, null,
                "System Admin", "admin@fuelnet.com", new Timestamp(System.currentTimeMillis()));
    }

    private String body(double price) throws Exception {
        RecordPriceRequest req = new RecordPriceRequest();
        req.setStationId(1L);
        req.setFuelType("PMS");
        req.setPrice(price);
        return objectMapper.writeValueAsString(req);
    }

    // ---------------------------------------------------------------- public reads

    @Test
    void currentPricesArePublic() throws Exception {
        when(fuelPriceService.getCurrentPrices()).thenReturn(List.of(sample()));

        mvc.perform(get("/api/fuelPrice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fuelType").value("PMS"))
                .andExpect(jsonPath("$[0].stationName").value("FuelNet Ugbowo"));
    }

    @Test
    void historyIsPublicAndPassesFilters() throws Exception {
        when(fuelPriceService.getHistory(1L, "PMS", 30)).thenReturn(List.of(sample()));

        mvc.perform(get("/api/fuelPrice/history").param("stationId", "1").param("fuelType", "PMS").param("limit", "30"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
        verify(fuelPriceService).getHistory(1L, "PMS", 30);
    }

    // ---------------------------------------------------------------- writes

    @Test
    void recordPriceWithoutTokenIs401() throws Exception {
        mvc.perform(post("/api/fuelPrice").contentType(MediaType.APPLICATION_JSON).content(body(900)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
        verifyNoInteractions(fuelPriceService);
    }

    @Test
    void recordPriceAsPlainUserIs403() throws Exception {
        mvc.perform(post("/api/fuelPrice")
                        .header("Authorization", tokenFor("jane@example.com", "ROLE_USER"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(900)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.status").value(403));
        verifyNoInteractions(fuelPriceService);
    }

    @Test
    void recordPriceWithGarbageTokenIs401() throws Exception {
        mvc.perform(post("/api/fuelPrice")
                        .header("Authorization", "Bearer not.a.jwt")
                        .contentType(MediaType.APPLICATION_JSON).content(body(900)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid or expired token"));
    }

    @Test
    void recordPriceAsAdminIs201AndUsesCallerAsSetBy() throws Exception {
        User admin = new User();
        admin.setEmail("admin@fuelnet.com");
        when(userService.requireUserByEmail("admin@fuelnet.com")).thenReturn(admin);
        when(fuelPriceService.recordPrice(any(RecordPriceRequest.class), eq(admin))).thenReturn(sample());

        mvc.perform(post("/api/fuelPrice")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(900)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.fuelType").value("PMS"));
    }

    @Test
    void recordPriceValidatesBody() throws Exception {
        mvc.perform(post("/api/fuelPrice")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(-5)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.price").value("Price must be greater than zero"));
        verifyNoInteractions(fuelPriceService);
    }

    @Test
    void unknownStationMapsTo404() throws Exception {
        when(userService.requireUserByEmail(any())).thenReturn(new User());
        when(fuelPriceService.recordPrice(any(), any())).thenThrow(new ResourceNotFoundException("Station not found: 1"));

        mvc.perform(post("/api/fuelPrice")
                        .header("Authorization", tokenFor("admin@fuelnet.com", "ROLE_ADMIN"))
                        .contentType(MediaType.APPLICATION_JSON).content(body(900)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Station not found: 1"));
    }

    @Test
    void auditLogRequiresAdmin() throws Exception {
        mvc.perform(get("/api/fuelPrice/audit"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/fuelPrice/audit").header("Authorization", tokenFor("jane@example.com", "ROLE_USER")))
                .andExpect(status().isForbidden());
    }
}
