package com.epistlecode.FuelNet.event;

import com.epistlecode.FuelNet.model.FuelPrice;

/** Published after a new price row is committed. */
public record PriceRecordedEvent(FuelPrice fuelPrice) {}
