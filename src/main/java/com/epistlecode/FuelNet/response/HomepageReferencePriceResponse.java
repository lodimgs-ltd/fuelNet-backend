package com.epistlecode.FuelNet.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class HomepageReferencePriceResponse {
    private String fuelName;
    private double price;
    private String stationName;
    private String stationAddress;
    private String lastUpdated;
    private String sourceUrl;
}
