package com.epistlecode.FuelNet.service;

import com.epistlecode.FuelNet.dto.AlertResponse;
import com.epistlecode.FuelNet.dto.CreateAlertRequest;
import com.epistlecode.FuelNet.exception.ConflictException;
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
import com.epistlecode.FuelNet.service.impl.PriceAlertServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PriceAlertServiceImplTest {

    @Mock PriceAlertRepository alertRepository;
    @Mock AlertNotificationRepository notificationRepository;
    @Mock FuelPriceRepository fuelPriceRepository;
    @Mock FuelTypeRepository fuelTypeRepository;
    @Mock StationRepository stationRepository;
    @Mock NotificationSender sender;

    PriceAlertServiceImpl service;

    Station ugbowo;
    Station sapele;
    FuelType pms;

    @BeforeEach
    void setUp() {
        service = new PriceAlertServiceImpl(alertRepository, notificationRepository, fuelPriceRepository,
                fuelTypeRepository, stationRepository, sender, "https://fuelnet.example");
        ugbowo = station(1L, "FuelNet Ugbowo");
        sapele = station(2L, "FuelNet Sapele Road");
        pms = new FuelType();
        pms.setId(10L);
        pms.setName("PMS");
        lenient().when(alertRepository.save(any(PriceAlert.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(notificationRepository.save(any(AlertNotification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private PriceAlert alert(Station station, PriceAlert.Condition cond, double threshold) {
        PriceAlert a = new PriceAlert();
        a.setId(7L);
        a.setEmail("jane@example.com");
        a.setFuelType(pms);
        a.setStation(station);
        a.setCondition(cond);
        a.setThreshold(threshold);
        a.setActive(true);
        a.setUnsubscribeToken("tok");
        a.setCreatedAt(new Timestamp(0));
        return a;
    }

    private FuelPrice price(Station station, double value, Double previous) {
        FuelPrice p = new FuelPrice();
        p.setId(100L);
        p.setStation(station);
        p.setFuelType(pms);
        p.setPrice(value);
        p.setPreviousPrice(previous);
        p.setCreatedAt(new Timestamp(System.currentTimeMillis()));
        return p;
    }

    // ------------------------------------------------------------ evaluate

    @Test
    void notifiesWhenPriceDropsBelowThreshold() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 870);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);

        int sent = service.evaluate(price(ugbowo, 860, 880.0));

        assertThat(sent).isEqualTo(1);
        assertThat(a.isTriggered()).isTrue();
        assertThat(a.getLastNotifiedAt()).isNotNull();

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(sender).send(eq("jane@example.com"), contains("PMS at FuelNet Ugbowo"), body.capture());
        assertThat(body.getValue())
                .contains("dropped below")
                .contains("870.00")
                .contains("(was ₦880.00)")
                .contains("https://fuelnet.example/alerts/unsubscribe/tok");

        ArgumentCaptor<AlertNotification> n = ArgumentCaptor.forClass(AlertNotification.class);
        verify(notificationRepository).save(n.capture());
        assertThat(n.getValue().getStatus()).isEqualTo(AlertNotification.Status.SENT);
    }

    @Test
    void doesNotNotifyWhenConditionNotMet() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 850);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));

        assertThat(service.evaluate(price(ugbowo, 860, null))).isZero();
        verifyNoInteractions(sender);
        assertThat(a.isTriggered()).isFalse();
    }

    @Test
    void isEdgeTriggeredAndDoesNotRepeatWhileConditionHolds() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 870);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);

        assertThat(service.evaluate(price(ugbowo, 860, null))).isEqualTo(1); // fires
        assertThat(service.evaluate(price(ugbowo, 855, null))).isZero();     // still below: silent
        assertThat(service.evaluate(price(ugbowo, 900, null))).isZero();     // back above: resets
        assertThat(a.isTriggered()).isFalse();
        assertThat(service.evaluate(price(ugbowo, 865, null))).isEqualTo(1); // fires again

        verify(sender, times(2)).send(anyString(), anyString(), anyString());
    }

    @Test
    void stationScopedAlertIgnoresOtherStations() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 870);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));

        assertThat(service.evaluate(price(sapele, 800, null))).isZero();
        verifyNoInteractions(sender);
    }

    @Test
    void networkWideAlertMatchesAnyStation() {
        PriceAlert a = alert(null, PriceAlert.Condition.ABOVE, 1000);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        FuelPrice sapelePrice = price(sapele, 1010, null);
        when(fuelPriceRepository.findCurrentPrices()).thenReturn(List.of(price(ugbowo, 950, null), sapelePrice));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(false);

        assertThat(service.evaluate(sapelePrice)).isEqualTo(1);

        ArgumentCaptor<AlertNotification> n = ArgumentCaptor.forClass(AlertNotification.class);
        verify(notificationRepository).save(n.capture());
        assertThat(n.getValue().getStatus()).isEqualTo(AlertNotification.Status.LOGGED);
    }

    @Test
    void senderFailureIsRecordedNotThrown() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 870);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(sender.send(anyString(), anyString(), anyString())).thenThrow(new RuntimeException("SMTP down"));

        assertThat(service.evaluate(price(ugbowo, 860, null))).isEqualTo(1);

        ArgumentCaptor<AlertNotification> n = ArgumentCaptor.forClass(AlertNotification.class);
        verify(notificationRepository).save(n.capture());
        assertThat(n.getValue().getStatus()).isEqualTo(AlertNotification.Status.FAILED);
        assertThat(n.getValue().getError()).isEqualTo("SMTP down");
    }

    @Test
    void networkWideAlertDoesNotFlapWhenOtherStationsDoNotSatisfyIt() {
        // Regression: iterating the board station-by-station used to reset the flag on a
        // non-matching station and then re-fire on the matching one, spamming the subscriber.
        PriceAlert a = alert(null, PriceAlert.Condition.ABOVE, 1100);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);
        List<FuelPrice> board = List.of(price(ugbowo, 1050, null), price(sapele, 1150, null));
        when(fuelPriceRepository.findCurrentPrices()).thenReturn(board);

        assertThat(service.sweep()).isEqualTo(1);   // fires once for Sapele
        assertThat(service.sweep()).isZero();       // still holds: silent
        assertThat(service.sweep()).isZero();
        assertThat(a.isTriggered()).isTrue();

        // A non-matching station changing must not reset the alert either.
        assertThat(service.evaluate(price(ugbowo, 1040, null))).isZero();
        assertThat(a.isTriggered()).isTrue();

        verify(sender, times(1)).send(anyString(), anyString(), anyString());
    }

    @Test
    void networkWideAlertNotifiesWithTheMostExtremePrice() {
        PriceAlert a = alert(null, PriceAlert.Condition.BELOW, 900);
        when(alertRepository.findByActiveTrue()).thenReturn(List.of(a));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);
        when(fuelPriceRepository.findCurrentPrices()).thenReturn(List.of(price(ugbowo, 880, null), price(sapele, 850, null)));

        assertThat(service.sweep()).isEqualTo(1);
        verify(sender).send(eq("jane@example.com"), contains("FuelNet Sapele Road"), contains("850.00"));
    }

    // ------------------------------------------------------------ subscribe

    @Test
    void subscribeNormalisesEmailAndGeneratesToken() {
        CreateAlertRequest req = new CreateAlertRequest();
        req.setEmail("  Jane@Example.com ");
        req.setFuelType("pms");
        req.setStationId(1L);
        req.setCondition(PriceAlert.Condition.BELOW);
        req.setThreshold(850.0);
        when(alertRepository.countByEmailIgnoreCaseAndActiveTrue("jane@example.com")).thenReturn(0L);
        when(fuelTypeRepository.findByNameIgnoreCase("pms")).thenReturn(Optional.of(pms));
        when(stationRepository.findById(1L)).thenReturn(Optional.of(ugbowo));
        when(fuelPriceRepository.findCurrentPrices()).thenReturn(List.of(price(ugbowo, 870, null)));

        AlertResponse r = service.subscribe(req);

        assertThat(r.email()).isEqualTo("jane@example.com");
        assertThat(r.stationName()).isEqualTo("FuelNet Ugbowo");
        assertThat(r.triggered()).isFalse(); // 870 is not below 850
        ArgumentCaptor<PriceAlert> saved = ArgumentCaptor.forClass(PriceAlert.class);
        verify(alertRepository, atLeastOnce()).save(saved.capture());
        assertThat(saved.getValue().getUnsubscribeToken()).hasSize(48);
    }

    @Test
    void subscribeNotifiesImmediatelyIfConditionAlreadyHolds() {
        CreateAlertRequest req = new CreateAlertRequest();
        req.setEmail("jane@example.com");
        req.setFuelType("PMS");
        req.setCondition(PriceAlert.Condition.BELOW);
        req.setThreshold(900.0);
        when(alertRepository.countByEmailIgnoreCaseAndActiveTrue("jane@example.com")).thenReturn(0L);
        when(fuelTypeRepository.findByNameIgnoreCase("PMS")).thenReturn(Optional.of(pms));
        when(fuelPriceRepository.findCurrentPrices()).thenReturn(List.of(price(ugbowo, 870, null)));
        when(sender.send(anyString(), anyString(), anyString())).thenReturn(true);

        AlertResponse r = service.subscribe(req);

        assertThat(r.triggered()).isTrue();
        verify(sender).send(eq("jane@example.com"), anyString(), anyString());
    }

    @Test
    void subscribeEnforcesPerEmailCap() {
        CreateAlertRequest req = new CreateAlertRequest();
        req.setEmail("jane@example.com");
        req.setFuelType("PMS");
        req.setCondition(PriceAlert.Condition.BELOW);
        req.setThreshold(900.0);
        when(alertRepository.countByEmailIgnoreCaseAndActiveTrue("jane@example.com")).thenReturn(10L);

        assertThatThrownBy(() -> service.subscribe(req)).isInstanceOf(ConflictException.class);
    }

    @Test
    void unsubscribeDeactivatesByToken() {
        PriceAlert a = alert(ugbowo, PriceAlert.Condition.BELOW, 870);
        when(alertRepository.findByUnsubscribeToken("tok")).thenReturn(Optional.of(a));

        assertThat(service.unsubscribe("tok").active()).isFalse();
    }

    private static Station station(long id, String name) {
        Station s = new Station();
        s.setId(id);
        s.setName(name);
        s.setAddress("addr");
        return s;
    }
}
