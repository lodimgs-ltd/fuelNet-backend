package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.config.JwtProvider;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.User;
import com.epistlecode.FuelNet.request.UpdatePriceRequest;
import com.epistlecode.FuelNet.response.MessageResponse;
import com.epistlecode.FuelNet.respository.FuelPriceRepository;
import com.epistlecode.FuelNet.respository.FuelTypeRepository;
import com.epistlecode.FuelNet.service.interfac.UserService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.*;

import java.sql.Timestamp;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/fuelPrice")
public class FuelPriceController {
    @Autowired
    FuelPriceRepository fuelPriceRepository;

    @Autowired
    FuelTypeRepository fuelTypeRepository;
    @Autowired
    JwtProvider jwtProvider;
    @Autowired
    UserService userService;

    @PutMapping("/{fuelType}")
    public ResponseEntity<?> updatePrice(
            @Valid @RequestBody UpdatePriceRequest req,
            BindingResult result,
            @PathVariable String fuelType,
            @RequestHeader("Authorization") String jwt
    ) {

        if (result.hasErrors()) {
            String errorMessage = result.getAllErrors().get(0).getDefaultMessage();
            return ResponseEntity.badRequest().body(Map.of("error", errorMessage));
        }

        try {
            String email = jwtProvider.getEmailFromJwtToken(jwt);
            User user = userService.findUserByEmail(email);
            FuelType fuel = fuelTypeRepository.findByName(fuelType);

            if (fuel == null) {
                return ResponseEntity.badRequest().body(Map.of("error", "Invalid fuel type: " + fuelType));
            }

            FuelPrice fuelPrice = fuelPriceRepository.findByFuelType(fuel);

            if (fuelPrice == null) {
                // Create new price record
                fuelPrice = new FuelPrice();
            }

            fuelPrice.setPrice(req.getPrice());
            fuelPrice.setFuelType(fuel);
            fuelPrice.setCreatedAt(new Timestamp(System.currentTimeMillis()));
            fuelPrice.setSetBy(user);

            fuelPriceRepository.save(fuelPrice);

            MessageResponse response = new MessageResponse();
            response.setMessage("Price " + (fuelPrice.getId() == null ? "created" : "updated") + " successfully");

            return ResponseEntity.ok(response);

        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", "Something went wrong while updating price"));
        }
    }


    @GetMapping("")
    public ResponseEntity<List<FuelPrice>> getFuelPrice(){
        List<FuelPrice> price = fuelPriceRepository.findAll();

        return new ResponseEntity<>(price, HttpStatus.OK);
    }

}
