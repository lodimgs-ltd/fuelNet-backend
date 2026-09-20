package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.AnalyticsDtos.AdminActivity;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.DailyChanges;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.FuelSummary;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.Overview;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.StateAverage;

import java.util.List;

public interface AnalyticsService {
    Overview getOverview();
    List<FuelSummary> getFuelSummaries();
    List<StateAverage> getStateAverages();
    List<DailyChanges> getChangesPerDay(int days);
    List<AdminActivity> getAdminActivity();
}
