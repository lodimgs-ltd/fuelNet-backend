package com.epistlecode.FuelNet.service.interfac;

import com.epistlecode.FuelNet.response.HomepageReferencePriceResponse;

import java.util.List;

public interface HomepageReferencePriceService {
    List<HomepageReferencePriceResponse> getLatestReferencePrices();
}
