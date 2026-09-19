package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.response.HomepageReferencePriceResponse;

import java.util.List;

public interface HomepageReferencePriceService {
    List<HomepageReferencePriceResponse> getLatestReferencePrices();
}
