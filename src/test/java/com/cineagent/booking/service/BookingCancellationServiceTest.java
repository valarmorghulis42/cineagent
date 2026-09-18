package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.domain.BookingSeatStatus;
import com.cineagent.booking.domain.BookingStatus;
import com.cineagent.booking.domain.ChargeType;
import com.cineagent.booking.repository.BookingChargeRepository;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingSeatRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.catalog.domain.City;
import com.cineagent.catalog.domain.Screen;
import com.cineagent.catalog.domain.Theater;
import com.cineagent.payment.domain.Payment;
import com.cineagent.payment.repository.PaymentRepository;
import com.cineagent.refund.service.RefundPolicyResolver;
import com.cineagent.refund.service.RefundService;
import com.cineagent.show.domain.Show;
import com.cineagent.show.service.SeatHoldService;
import com.cineagent.show.service.ShowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.DefaultTransactionDefinition;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCancellationServiceTest {

    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private BookingChargeRepository bookingChargeRepository;
    @Mock private BookingStatusHistoryRepository historyRepository;
    @Mock private PaymentRepository paymentRepository;
    @Mock private SeatHoldService seatHoldService;
    @Mock private ShowService showService;
    @Mock private RefundPolicyResolver refundPolicyResolver;
    @Mock private RefundService refundService;
    @Mock private ApplicationEventPublisher eventPublisher;
    @Mock private PlatformTransactionManager transactionManager;

    private BookingCancellationService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneId.of("UTC"));
        // Mock the transaction manager so TransactionTemplate proceeds without errors
        when(transactionManager.getTransaction(any())).thenReturn(mock(TransactionStatus.class));
        
        service = new BookingCancellationService(
                bookingRepository, bookingSeatRepository, bookingChargeRepository, historyRepository,
                paymentRepository, seatHoldService, showService, refundPolicyResolver, refundService,
                eventPublisher, transactionManager, clock);
    }

    @Test
    void cancelSeats_proRataRefund_onlyRefundableCharges() {
        // Arrange
        Long bookingId = 100L;
        Long userId = 200L;
        List<Long> targetSeatIds = List.of(1L, 2L);

        Booking booking = mock(Booking.class);
        when(booking.getUserId()).thenReturn(userId);
        when(booking.getStatus()).thenReturn(BookingStatus.CONFIRMED);
        when(booking.getShowId()).thenReturn(300L);
        when(booking.getBookingReference()).thenReturn("REF-123");
        when(bookingRepository.findById(bookingId)).thenReturn(Optional.of(booking));
        
        BookingSeat seat1 = mock(BookingSeat.class);
        when(seat1.getId()).thenReturn(1L);
        when(seat1.getStatus()).thenReturn(BookingSeatStatus.ACTIVE);
        
        BookingSeat seat2 = mock(BookingSeat.class);
        when(seat2.getId()).thenReturn(2L);
        when(seat2.getStatus()).thenReturn(BookingSeatStatus.ACTIVE);
        
        when(bookingSeatRepository.findByBookingId(bookingId)).thenReturn(List.of(seat1, seat2));

        Payment payment = mock(Payment.class);
        when(payment.getId()).thenReturn(999L);
        when(payment.getGatewayReference()).thenReturn("GW-123");
        when(paymentRepository.findByBookingId(bookingId)).thenReturn(Optional.of(payment));

        Show show = mock(Show.class);
        when(show.getId()).thenReturn(300L);
        when(show.getStartsAt()).thenReturn(Instant.parse("2026-09-19T02:00:00Z")); // 120 mins later
        Screen screen = mock(Screen.class);
        Theater theater = mock(Theater.class);
        when(theater.getId()).thenReturn(400L);
        when(screen.getTheater()).thenReturn(theater);
        when(show.getScreen()).thenReturn(screen);
        City city = mock(City.class);
        when(city.getId()).thenReturn(500L);
        when(show.getCity()).thenReturn(city);
        
        when(showService.getShow(300L)).thenReturn(show);
        
        // Let's say 50% refund policy
        when(refundPolicyResolver.resolvePercentage(300L, 400L, 500L, 120)).thenReturn(new BigDecimal("50.00"));

        // Charges
        BookingCharge refundableCharge1 = new BookingCharge(bookingId, 1L, ChargeType.BASE, "Base", new BigDecimal("10.00"), true);
        BookingCharge nonRefundableCharge1 = new BookingCharge(bookingId, 1L, ChargeType.FEE, "Fee", new BigDecimal("2.00"), false);
        BookingCharge refundableCharge2 = new BookingCharge(bookingId, 2L, ChargeType.BASE, "Base", new BigDecimal("10.00"), true);
        
        when(bookingChargeRepository.findByBookingSeatIdIn(List.of(1L, 2L))).thenReturn(List.of(refundableCharge1, nonRefundableCharge1, refundableCharge2));

        // Act
        service.cancelSeats(bookingId, userId, targetSeatIds, "User requested");

        // Assert
        // Sum of refundable = 10.00 + 10.00 = 20.00
        // Refund amount = 50% of 20.00 = 10.00
        verify(refundService).issueRefund(eq(bookingId), eq(999L), eq("GW-123"), eq(new BigDecimal("10.00")), eq("User requested"));
    }
}
