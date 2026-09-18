package com.cineagent.booking.service;

import com.cineagent.booking.domain.Booking;
import com.cineagent.booking.domain.BookingCharge;
import com.cineagent.booking.domain.BookingSeat;
import com.cineagent.booking.repository.BookingChargeRepository;
import com.cineagent.booking.repository.BookingRepository;
import com.cineagent.booking.repository.BookingSeatRepository;
import com.cineagent.booking.repository.BookingStatusHistoryRepository;
import com.cineagent.catalog.domain.SeatCategory;
import com.cineagent.pricing.domain.DiscountCode;
import com.cineagent.pricing.domain.DiscountType;
import com.cineagent.pricing.service.DiscountService;
import com.cineagent.show.domain.SeatHold;
import com.cineagent.show.domain.Show;
import com.cineagent.show.domain.ShowPrice;
import com.cineagent.show.domain.ShowSeat;
import com.cineagent.catalog.domain.Seat;
import com.cineagent.show.service.SeatHoldService;
import com.cineagent.show.service.SeatHoldService.HeldSeatsSnapshot;
import com.cineagent.show.service.ShowService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingCreateServiceTest {

    @Mock private SeatHoldService seatHoldService;
    @Mock private ShowService showService;
    @Mock private DiscountService discountService;
    @Mock private BookingRepository bookingRepository;
    @Mock private BookingSeatRepository bookingSeatRepository;
    @Mock private BookingChargeRepository bookingChargeRepository;
    @Mock private BookingStatusHistoryRepository historyRepository;
    @Mock private BookingReferenceGenerator referenceGenerator;

    @Captor private ArgumentCaptor<List<BookingCharge>> chargesCaptor;

    private BookingCreateService service;
    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-09-19T00:00:00Z"), ZoneId.of("UTC"));
        service = new BookingCreateService(seatHoldService, showService, discountService, bookingRepository, bookingSeatRepository, bookingChargeRepository, historyRepository, referenceGenerator, clock);
    }

    @Test
    void create_proRataDiscountAllocation_exactSumWithRounding() {
        // Arrange
        Show show = mock(Show.class);
        when(show.getId()).thenReturn(1L);
        when(show.getCurrency()).thenReturn("USD");
        
        SeatHold hold = mock(SeatHold.class);
        when(hold.getShow()).thenReturn(show);
        
        // 3 unevenly-priced seats: 15.00, 15.00, 20.00
        ShowSeat seat1 = mock(ShowSeat.class);
        Seat s1 = mock(Seat.class);
        when(s1.getCategory()).thenReturn(SeatCategory.REGULAR);
        when(s1.getDisplayLabel()).thenReturn("A1");
        when(seat1.getSeat()).thenReturn(s1);
        
        ShowSeat seat2 = mock(ShowSeat.class);
        Seat s2 = mock(Seat.class);
        when(s2.getCategory()).thenReturn(SeatCategory.REGULAR);
        when(s2.getDisplayLabel()).thenReturn("A2");
        when(seat2.getSeat()).thenReturn(s2);
        
        ShowSeat seat3 = mock(ShowSeat.class);
        Seat s3 = mock(Seat.class);
        when(s3.getCategory()).thenReturn(SeatCategory.PREMIUM);
        when(s3.getDisplayLabel()).thenReturn("A3");
        when(seat3.getSeat()).thenReturn(s3);
        
        when(seatHoldService.lockOwnedActiveHold(1L, 100L)).thenReturn(new HeldSeatsSnapshot(hold, List.of(seat1, seat2, seat3)));
        
        ShowPrice sp1 = mock(ShowPrice.class);
        when(sp1.getSeatCategory()).thenReturn(SeatCategory.REGULAR);
        when(sp1.getBaseAmount()).thenReturn(new BigDecimal("15.00"));
        
        ShowPrice sp2 = mock(ShowPrice.class);
        when(sp2.getSeatCategory()).thenReturn(SeatCategory.PREMIUM);
        when(sp2.getBaseAmount()).thenReturn(new BigDecimal("20.00"));
        
        when(showService.getPrices(1L)).thenReturn(List.of(sp1, sp2));
        
        // Subtotal = 50.00
        // 10% discount = -5.00
        DiscountCode discountCode = new DiscountCode("10OFF", DiscountType.PERCENTAGE, new BigDecimal("10.00"), BigDecimal.ZERO, null, 100, Instant.MIN, Instant.MAX);
        when(discountService.validate("10OFF", new BigDecimal("50.00"))).thenReturn(discountCode);
        
        when(referenceGenerator.generate()).thenReturn("REF123");
        
        Booking savedBooking = new Booking("REF123", 100L, 1L, 1L, 3, new BigDecimal("45.00"), "USD", discountCode.getId());
        when(bookingRepository.save(any(Booking.class))).thenReturn(savedBooking);
        
        when(bookingSeatRepository.save(any(BookingSeat.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        Booking booking = service.create(1L, 100L, "10OFF");

        // Assert
        verify(bookingChargeRepository).saveAll(chargesCaptor.capture());
        List<BookingCharge> charges = chargesCaptor.getValue();
        
        // Total should be 45.00. 
        assertThat(booking.getTotalAmount()).isEqualByComparingTo("45.00");
        
        // Sum of charges must exactly equal 45.00
        BigDecimal sumOfCharges = charges.stream().map(BookingCharge::getAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(sumOfCharges).isEqualByComparingTo("45.00");
        
        // Verify discount allocations
        // Expected discount per standard seat (15.00) = 15/50 * 5.00 = 1.50
        // Expected discount for premium seat (20.00) absorbs remainder: 5.00 - 1.50 - 1.50 = 2.00
        // Since discount lines are negative, they should be -1.50, -1.50, -2.00
        List<BookingCharge> discountCharges = charges.stream().filter(c -> !c.isRefundable() || c.getDescription().startsWith("Discount")).toList();
        
        assertThat(discountCharges).hasSize(3);
        assertThat(discountCharges.get(0).getAmount()).isEqualByComparingTo("-1.50");
        assertThat(discountCharges.get(1).getAmount()).isEqualByComparingTo("-1.50");
        assertThat(discountCharges.get(2).getAmount()).isEqualByComparingTo("-2.00");
    }
}
