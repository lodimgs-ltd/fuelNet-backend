package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.dto.AnalyticsDtos.AdminActivity;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.DailyChanges;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.FuelSummary;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.Overview;
import com.epistlecode.FuelNet.dto.AnalyticsDtos.StateAverage;
import com.epistlecode.FuelNet.model.AlertNotification;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.StationStatus;
import com.epistlecode.FuelNet.repository.AlertNotificationRepository;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.FuelTypeRepository;
import com.epistlecode.FuelNet.repository.PriceAlertRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.AnalyticsService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregations over the append-only price table. Everything here is computed
 * in memory from at most two "board" snapshots plus a few count queries, which
 * is fine for hundreds of stations. Push into SQL if the network grows large.
 */
@Service
@Transactional(readOnly = true)
public class AnalyticsServiceImpl implements AnalyticsService {

    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final StationRepository stationRepository;
    private final PriceAlertRepository alertRepository;
    private final AlertNotificationRepository notificationRepository;

    public AnalyticsServiceImpl(FuelPriceRepository fuelPriceRepository,
                                FuelTypeRepository fuelTypeRepository,
                                StationRepository stationRepository,
                                PriceAlertRepository alertRepository,
                                AlertNotificationRepository notificationRepository) {
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.stationRepository = stationRepository;
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public Overview getOverview() {
        Timestamp weekAgo = ago(Duration.ofDays(7));
        Timestamp monthAgo = ago(Duration.ofDays(30));
        return new Overview(
                stationRepository.count(),
                stationRepository.findByStatusOrderByNameAsc(StationStatus.ACTIVE).size(),
                fuelTypeRepository.count(),
                fuelPriceRepository.count(),
                fuelPriceRepository.countByCreatedAtAfter(weekAgo),
                fuelPriceRepository.countByCreatedAtAfter(monthAgo),
                alertRepository.countByActiveTrue(),
                notificationRepository.countByStatus(AlertNotification.Status.SENT)
                        + notificationRepository.countByStatus(AlertNotification.Status.LOGGED),
                getFuelSummaries()
        );
    }

    @Override
    public List<FuelSummary> getFuelSummaries() {
        Timestamp weekAgo = ago(Duration.ofDays(7));
        List<FuelPrice> now = fuelPriceRepository.findCurrentPrices();
        List<FuelPrice> then = fuelPriceRepository.findBoardAsOf(weekAgo);

        Map<String, List<FuelPrice>> nowByFuel = groupByFuel(now);
        Map<String, List<FuelPrice>> thenByFuel = groupByFuel(then);

        List<FuelSummary> out = new ArrayList<>();
        for (Map.Entry<String, List<FuelPrice>> e : nowByFuel.entrySet()) {
            String fuel = e.getKey();
            List<FuelPrice> rows = e.getValue();
            double avg = round2(rows.stream().mapToDouble(FuelPrice::getPrice).average().orElse(0));
            FuelPrice min = rows.stream().min(Comparator.comparingDouble(FuelPrice::getPrice)).orElseThrow();
            FuelPrice max = rows.stream().max(Comparator.comparingDouble(FuelPrice::getPrice)).orElseThrow();

            Double avgThen = null, change = null, changePct = null;
            List<FuelPrice> prev = thenByFuel.get(fuel);
            if (prev != null && !prev.isEmpty()) {
                avgThen = round2(prev.stream().mapToDouble(FuelPrice::getPrice).average().orElse(0));
                change = round2(avg - avgThen);
                changePct = avgThen == 0 ? null : round2(change / avgThen * 100);
            }
            long changes7d = fuelPriceRepository.countByFuelTypeAndCreatedAtAfter(rows.get(0).getFuelType(), weekAgo);

            out.add(new FuelSummary(fuel, rows.size(), avg,
                    min.getPrice(), min.getStation().getName(),
                    max.getPrice(), max.getStation().getName(),
                    avgThen, change, changePct, changes7d));
        }
        out.sort(Comparator.comparing(FuelSummary::fuelType));
        return out;
    }

    @Override
    public List<StateAverage> getStateAverages() {
        // state → fuel → prices
        Map<String, Map<String, List<Double>>> byState = new TreeMap<>();
        for (FuelPrice p : fuelPriceRepository.findCurrentPrices()) {
            String state = p.getStation().getState();
            if (state == null || state.isBlank()) state = "Unknown";
            byState.computeIfAbsent(state, k -> new TreeMap<>())
                    .computeIfAbsent(p.getFuelType().getName(), k -> new ArrayList<>())
                    .add(p.getPrice());
        }
        List<StateAverage> out = new ArrayList<>();
        for (var stateEntry : byState.entrySet()) {
            for (var fuelEntry : stateEntry.getValue().entrySet()) {
                List<Double> prices = fuelEntry.getValue();
                out.add(new StateAverage(stateEntry.getKey(), fuelEntry.getKey(), prices.size(),
                        round2(prices.stream().mapToDouble(Double::doubleValue).average().orElse(0)),
                        prices.stream().mapToDouble(Double::doubleValue).min().orElse(0),
                        prices.stream().mapToDouble(Double::doubleValue).max().orElse(0)));
            }
        }
        return out;
    }

    @Override
    public List<DailyChanges> getChangesPerDay(int days) {
        int span = Math.max(1, Math.min(days, 365));
        LocalDate today = LocalDate.now(ZoneId.systemDefault());
        LocalDate start = today.minusDays(span - 1L);
        Timestamp since = Timestamp.valueOf(start.atStartOfDay());

        // Zero-fill so the chart has a point for every day.
        Map<String, Long> counts = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(today); d = d.plusDays(1)) {
            counts.put(d.toString(), 0L);
        }
        for (Object[] row : fuelPriceRepository.countChangesPerDay(since)) {
            String day = row[0] instanceof java.sql.Date sd ? sd.toLocalDate().toString() : String.valueOf(row[0]);
            counts.merge(day, ((Number) row[1]).longValue(), Long::sum);
        }
        return counts.entrySet().stream().map(e -> new DailyChanges(e.getKey(), e.getValue())).toList();
    }

    @Override
    public List<AdminActivity> getAdminActivity() {
        return fuelPriceRepository.countChangesPerAdmin().stream()
                .map(r -> new AdminActivity(((Number) r[0]).longValue(), (String) r[1], (String) r[2],
                        ((Number) r[3]).longValue(), (Timestamp) r[4]))
                .toList();
    }

    // ------------------------------------------------------------ helpers

    private static Map<String, List<FuelPrice>> groupByFuel(List<FuelPrice> rows) {
        Map<String, List<FuelPrice>> out = new TreeMap<>();
        for (FuelPrice p : rows) {
            out.computeIfAbsent(p.getFuelType().getName(), k -> new ArrayList<>()).add(p);
        }
        return out;
    }

    private static Timestamp ago(Duration d) {
        return Timestamp.from(Instant.now().minus(d));
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }
}
