package com.epistlecode.FuelNet.service.impl;

import com.epistlecode.FuelNet.dto.AlertNotificationResponse;
import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.dto.CreateAlertRequest;
import com.epistlecode.FuelNet.dto.PageResponse;
import com.epistlecode.FuelNet.event.PriceRecordedEvent;
import com.epistlecode.FuelNet.exception.ConflictException;
import com.epistlecode.FuelNet.exception.ResourceNotFoundException;
import com.epistlecode.FuelNet.model.AlertNotification;
import com.epistlecode.FuelNet.model.FuelPrice;
import com.epistlecode.FuelNet.model.FuelType;
import com.epistlecode.FuelNet.model.PriceAlert;
import com.epistlecode.FuelNet.model.Station;
import com.epistlecode.FuelNet.notification.NotificationSender;
import com.epistlecode.FuelNet.repository.AlertNotificationRepository;
import com.epistlecode.FuelNet.repository.FuelPriceRepository;
import com.epistlecode.FuelNet.repository.FuelTypeRepository;
import com.epistlecode.FuelNet.repository.PriceAlertRepository;
import com.epistlecode.FuelNet.repository.StationRepository;
import com.epistlecode.FuelNet.service.PriceAlertService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.security.SecureRandom;
import java.sql.Timestamp;
import java.util.HexFormat;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

@Service
@Transactional
public class PriceAlertServiceImpl implements PriceAlertService {

    private static final Logger log = LoggerFactory.getLogger(PriceAlertServiceImpl.class);
    private static final int MAX_ACTIVE_PER_EMAIL = 10;
    private static final SecureRandom RANDOM = new SecureRandom();

    private final PriceAlertRepository alertRepository;
    private final AlertNotificationRepository notificationRepository;
    private final FuelPriceRepository fuelPriceRepository;
    private final FuelTypeRepository fuelTypeRepository;
    private final StationRepository stationRepository;
    private final NotificationSender sender;
    private final String publicBaseUrl;

    public PriceAlertServiceImpl(PriceAlertRepository alertRepository,
                                 AlertNotificationRepository notificationRepository,
                                 FuelPriceRepository fuelPriceRepository,
                                 FuelTypeRepository fuelTypeRepository,
                                 StationRepository stationRepository,
                                 NotificationSender sender,
                                 @Value("${app.public-base-url:http://localhost:5173}") String publicBaseUrl) {
        this.alertRepository = alertRepository;
        this.notificationRepository = notificationRepository;
        this.fuelPriceRepository = fuelPriceRepository;
        this.fuelTypeRepository = fuelTypeRepository;
        this.stationRepository = stationRepository;
        this.sender = sender;
        this.publicBaseUrl = publicBaseUrl;
    }

    // ------------------------------------------------------------ subscribe

    @Override
    public AlertResponse subscribe(CreateAlertRequest req) {
        String email = req.getEmail().trim().toLowerCase(Locale.ROOT);
        if (alertRepository.countByEmailIgnoreCaseAndActiveTrue(email) >= MAX_ACTIVE_PER_EMAIL) {
            throw new ConflictException("You already have the maximum of " + MAX_ACTIVE_PER_EMAIL + " active alerts");
        }
        FuelType fuelType = fuelTypeRepository.findByNameIgnoreCase(req.getFuelType())
                .orElseThrow(() -> new ResourceNotFoundException("Unknown fuel type: " + req.getFuelType()));
        Station station = null;
        if (req.getStationId() != null) {
            station = stationRepository.findById(req.getStationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Station not found: " + req.getStationId()));
        }

        PriceAlert alert = new PriceAlert();
        alert.setEmail(email);
        alert.setFuelType(fuelType);
        alert.setStation(station);
        alert.setCondition(req.getCondition());
        alert.setThreshold(req.getThreshold());
        alert.setActive(true);
        alert.setUnsubscribeToken(newToken());
        alert.setCreatedAt(now());

        // If the condition already holds, mark it triggered so the subscriber is
        // notified now (via sweep/evaluate) rather than waiting for the next change.
        alert = alertRepository.save(alert);
        evaluateAgainstCurrentBoard(alert);
        return AlertResponse.from(alert);
    }

    @Override
    public AlertResponse unsubscribe(String token) {
        PriceAlert alert = alertRepository.findByUnsubscribeToken(token)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found"));
        alert.setActive(false);
        return AlertResponse.from(alertRepository.save(alert));
    }

    // ------------------------------------------------------------ evaluation

    /** Runs after the price-recording transaction commits so we never notify on a rolled-back price. */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.REQUIRES_NEW)
    public void onPriceRecorded(PriceRecordedEvent event) {
        int sent = evaluate(event.fuelPrice());
        if (sent > 0) {
            log.info("Price change #{} triggered {} alert notification(s)", event.fuelPrice().getId(), sent);
        }
    }

    @Override
    public int evaluate(FuelPrice price) {
        int sent = 0;
        List<FuelPrice> board = null; // loaded lazily, only if a network-wide alert needs it
        for (PriceAlert alert : alertRepository.findByActiveTrue()) {
            if (!alert.getFuelType().getId().equals(price.getFuelType().getId())) continue;
            List<FuelPrice> candidates;
            if (alert.getStation() != null) {
                if (price.getStation() == null || !alert.getStation().getId().equals(price.getStation().getId())) continue;
                candidates = List.of(price);
            } else {
                // "Any station": the new price may not be the one that satisfies the alert,
                // so judge against the whole board rather than this single row.
                if (board == null) board = fuelPriceRepository.findCurrentPrices();
                candidates = board;
            }
            sent += applyState(alert, candidates) ? 1 : 0;
        }
        return sent;
    }

    /** Periodic safety net (default every 15 minutes) in case an event was missed. */
    @Scheduled(fixedDelayString = "${app.alerts.sweep-ms:900000}", initialDelayString = "${app.alerts.sweep-ms:900000}")
    @Override
    public int sweep() {
        List<FuelPrice> board = fuelPriceRepository.findCurrentPrices();
        int sent = 0;
        for (PriceAlert alert : alertRepository.findByActiveTrue()) {
            sent += applyState(alert, board) ? 1 : 0;
        }
        if (sent > 0) log.info("Alert sweep sent {} notification(s)", sent);
        return sent;
    }

    private void evaluateAgainstCurrentBoard(PriceAlert alert) {
        applyState(alert, fuelPriceRepository.findCurrentPrices());
    }

    private static boolean matches(PriceAlert alert, FuelPrice price) {
        if (price.getStation() == null) return false;
        if (!alert.getFuelType().getId().equals(price.getFuelType().getId())) return false;
        return alert.getStation() == null || alert.getStation().getId().equals(price.getStation().getId());
    }

    static boolean conditionHolds(PriceAlert.Condition condition, double threshold, double price) {
        return condition == PriceAlert.Condition.BELOW ? price < threshold : price > threshold;
    }

    /**
     * Edge-triggered per alert: the condition "holds" if <em>any</em> matching candidate price
     * satisfies it. Notify when it becomes true (using the most extreme matching price);
     * reset silently when it becomes false.
     * @return true if a notification was sent
     */
    private boolean applyState(PriceAlert alert, List<FuelPrice> candidates) {
        Comparator<FuelPrice> byPrice = Comparator.comparingDouble(FuelPrice::getPrice);
        Optional<FuelPrice> best = candidates.stream()
                .filter(p -> matches(alert, p))
                .filter(p -> conditionHolds(alert.getCondition(), alert.getThreshold(), p.getPrice()))
                .min(alert.getCondition() == PriceAlert.Condition.BELOW ? byPrice : byPrice.reversed());

        if (best.isPresent() && !alert.isTriggered()) {
            alert.setTriggered(true);
            alert.setLastNotifiedAt(now());
            alertRepository.save(alert);
            notify(alert, best.get());
            return true;
        }
        if (best.isEmpty() && alert.isTriggered()) {
            // Only reset if we actually had something to judge against.
            boolean anyMatching = candidates.stream().anyMatch(p -> matches(alert, p));
            if (anyMatching) {
                alert.setTriggered(false);
                alertRepository.save(alert);
            }
        }
        return false;
    }

    private void notify(PriceAlert alert, FuelPrice price) {
        String where = price.getStation().getName();
        String direction = alert.getCondition() == PriceAlert.Condition.BELOW ? "dropped below" : "risen above";
        String subject = "FuelNet: %s at %s is now ₦%,.2f".formatted(price.getFuelType().getName(), where, price.getPrice());
        String body = """
                %s at %s has %s your alert threshold of ₦%,.2f.

                Current price: ₦%,.2f per litre%s
                Station: %s, %s

                To stop receiving this alert: %s/alerts/unsubscribe/%s
                """.formatted(
                price.getFuelType().getName(), where, direction, alert.getThreshold(),
                price.getPrice(),
                price.getPreviousPrice() != null ? " (was ₦%,.2f)".formatted(price.getPreviousPrice()) : "",
                where, price.getStation().getAddress(),
                publicBaseUrl, alert.getUnsubscribeToken());

        AlertNotification n = new AlertNotification();
        n.setAlert(alert);
        n.setFuelPrice(price);
        n.setEmail(alert.getEmail());
        n.setMessage(subject);
        n.setCreatedAt(now());
        try {
            boolean delivered = sender.send(alert.getEmail(), subject, body);
            n.setStatus(delivered ? AlertNotification.Status.SENT : AlertNotification.Status.LOGGED);
        } catch (Exception e) {
            log.warn("Failed to send alert to {}: {}", alert.getEmail(), e.getMessage());
            n.setStatus(AlertNotification.Status.FAILED);
            n.setError(e.getMessage() != null ? e.getMessage().substring(0, Math.min(500, e.getMessage().length())) : "unknown");
        }
        notificationRepository.save(n);
    }

    // ------------------------------------------------------------ admin

    @Override
    @Transactional(readOnly = true)
    public List<AlertResponse> getAlerts() {
        return alertRepository.findAllByOrderByCreatedAtDesc().stream().map(AlertResponse::from).toList();
    }

    @Override
    public AlertResponse setActive(Long id, boolean active) {
        PriceAlert alert = alertRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Alert not found: " + id));
        alert.setActive(active);
        return AlertResponse.from(alertRepository.save(alert));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AlertNotificationResponse> getNotifications(int page, int size) {
        var pageable = PageRequest.of(Math.max(0, page), Math.max(1, Math.min(size, 100)));
        return PageResponse.of(notificationRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(AlertNotificationResponse::from));
    }

    @Override
    public String channel() {
        return sender.channel();
    }

    // ------------------------------------------------------------ helpers

    private static String newToken() {
        byte[] bytes = new byte[24];
        RANDOM.nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    private static Timestamp now() {
        return new Timestamp(System.currentTimeMillis());
    }
}
