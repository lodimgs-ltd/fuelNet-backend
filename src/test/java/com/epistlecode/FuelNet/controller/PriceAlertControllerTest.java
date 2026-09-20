package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.config.SecurityConfig;
import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.exception.GlobalExceptionHandler;
import com.epistlecode.FuelNet.model.PriceAlert;
import com.epistlecode.FuelNet.service.PriceAlertService;
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

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({PriceAlertController.class, AdminAlertController.class})
@Import({SecurityConfig.class, JwtProvider.class, GlobalExceptionHandler.class})
@TestPropertySource(properties = {
        "app.jwt.secret=test-secret-key-for-fuelnet-unit-tests-32-bytes-min",
        "app.cors.allowed-origins=http://localhost:5173"
})
class PriceAlertControllerTest {

    @Autowired MockMvc mvc;
    @Autowired JwtProvider jwtProvider;
    @MockitoBean PriceAlertService alertService;

    private String adminToken() {
        return "Bearer " + jwtProvider.generateToken(new UsernamePasswordAuthenticationToken(
                "admin@fuelnet.com", null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))));
    }

    private static AlertResponse sample() {
        return new AlertResponse(1L, "jane@example.com", "PMS", 1L, "FuelNet Ugbowo",
                PriceAlert.Condition.BELOW, 850, true, false, null, null);
    }

    @Test
    void anyoneCanSubscribe() throws Exception {
        when(alertService.subscribe(any())).thenReturn(sample());

        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"jane@example.com","fuelType":"PMS","stationId":1,"condition":"BELOW","threshold":850}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("jane@example.com"))
                .andExpect(jsonPath("$.condition").value("BELOW"));
    }

    @Test
    void subscribeValidatesInput() throws Exception {
        mvc.perform(post("/api/alerts").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"not-an-email","fuelType":"","condition":"BELOW","threshold":-1}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.email").exists())
                .andExpect(jsonPath("$.fieldErrors.fuelType").exists())
                .andExpect(jsonPath("$.fieldErrors.threshold").exists());
        verifyNoInteractions(alertService);
    }

    @Test
    void unsubscribeIsPublic() throws Exception {
        when(alertService.unsubscribe("abc")).thenReturn(sample());
        mvc.perform(post("/api/alerts/unsubscribe/abc")).andExpect(status().isOk());
    }

    @Test
    void adminEndpointsRequireAdmin() throws Exception {
        mvc.perform(get("/api/admin/alerts")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/alerts/sweep")).andExpect(status().isUnauthorized());

        when(alertService.getAlerts()).thenReturn(List.of(sample()));
        mvc.perform(get("/api/admin/alerts").header("Authorization", adminToken()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].stationName").value("FuelNet Ugbowo"));
    }
}
