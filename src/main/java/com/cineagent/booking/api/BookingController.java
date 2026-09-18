package com.cineagent.booking.api;

import com.cineagent.booking.api.dto.BookingDtos.BookingChargeResponse;
import com.cineagent.booking.api.dto.BookingDtos.BookingResponse;
import com.cineagent.booking.api.dto.BookingDtos.BookingSeatResponse;
import com.cineagent.booking.api.dto.BookingDtos.BookingStatusHistoryResponse;
import com.cineagent.booking.api.dto.BookingDtos.CancelSeatsRequest;
import com.cineagent.booking.api.dto.BookingDtos.CreateBookingRequest;
import com.cineagent.booking.service.BookingCancellationService;
import com.cineagent.booking.service.BookingCreateService;
import com.cineagent.booking.service.BookingPaymentService;
import com.cineagent.booking.service.BookingQueryService;
import com.cineagent.common.security.AuthenticatedUser;
import com.cineagent.common.security.CurrentUser;
import com.cineagent.common.web.PageResponse;
import jakarta.validation.Valid;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

  private final BookingCreateService createService;
  private final BookingPaymentService paymentService;
  private final BookingCancellationService cancellationService;
  private final BookingQueryService queryService;

  public BookingController(
      BookingCreateService createService,
      BookingPaymentService paymentService,
      BookingCancellationService cancellationService,
      BookingQueryService queryService) {
    this.createService = createService;
    this.paymentService = paymentService;
    this.cancellationService = cancellationService;
    this.queryService = queryService;
  }

  @PostMapping
  public ResponseEntity<BookingResponse> create(
      @Valid @RequestBody CreateBookingRequest req, @CurrentUser AuthenticatedUser user) {
    var booking = createService.create(req.holdId(), user.userId(), req.discountCode());
    return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
  }

  @PostMapping("/{id}/pay")
  public BookingResponse pay(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    return BookingResponse.from(paymentService.pay(id, user.userId()));
  }

  @PostMapping("/{id}/cancel-seats")
  public BookingResponse cancelSeats(
      @PathVariable Long id, @Valid @RequestBody CancelSeatsRequest req, @CurrentUser AuthenticatedUser user) {
    return BookingResponse.from(
        cancellationService.cancelSeats(id, user.userId(), req.bookingSeatIds(), req.reason()));
  }

  @PostMapping("/{id}/cancel")
  public BookingResponse cancel(
      @PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    return BookingResponse.from(cancellationService.cancelEntireBooking(id, user.userId(), "Customer cancellation"));
  }

  @GetMapping("/{id}")
  public BookingResponse get(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    return BookingResponse.from(queryService.getOwned(id, user.userId()));
  }

  @GetMapping
  public PageResponse<BookingResponse> listMine(
      @CurrentUser AuthenticatedUser user, @PageableDefault(size = 20, sort = "id") Pageable pageable) {
    var page = queryService.listMine(user.userId(), pageable);
    return PageResponse.from(page, page.getContent().stream().map(BookingResponse::from).toList());
  }

  @GetMapping("/{id}/seats")
  public java.util.List<BookingSeatResponse> seats(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    queryService.getOwned(id, user.userId());
    return queryService.getSeats(id).stream().map(BookingSeatResponse::from).toList();
  }

  @GetMapping("/{id}/charges")
  public java.util.List<BookingChargeResponse> charges(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    queryService.getOwned(id, user.userId());
    return queryService.getCharges(id).stream().map(BookingChargeResponse::from).toList();
  }

  @GetMapping("/{id}/history")
  public java.util.List<BookingStatusHistoryResponse> history(@PathVariable Long id, @CurrentUser AuthenticatedUser user) {
    queryService.getOwned(id, user.userId());
    return queryService.getHistory(id).stream().map(BookingStatusHistoryResponse::from).toList();
  }
}
