package com.epistlecode.FuelNet.controller;

import com.epistlecode.FuelNet.response.HomepageReferencePriceResponse;
import com.epistlecode.FuelNet.service.HomepageReferencePriceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/homepage")
public class HomepageReferencePriceController {

    @Autowired
    private HomepageReferencePriceService homepageReferencePriceService;

    @GetMapping("/reference-prices")
    public ResponseEntity<List<HomepageReferencePriceResponse>> getReferencePrices() {
        return new ResponseEntity<>(homepageReferencePriceService.getLatestReferencePrices(), HttpStatus.OK);
    }
}
