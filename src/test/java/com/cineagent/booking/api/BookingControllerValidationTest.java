package com.cineagent.booking.api;

import com.cineagent.booking.service.BookingCancellationService;
import com.cineagent.booking.service.BookingCreateService;
import com.cineagent.booking.service.BookingPaymentService;
import com.cineagent.booking.service.BookingQueryService;
import com.cineagent.common.error.GlobalExceptionHandler;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import com.cineagent.identity.security.JwtAuthenticationFilter;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = BookingController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class BookingControllerValidationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private BookingCreateService createService;
    @MockitoBean private BookingPaymentService paymentService;
    @MockitoBean private BookingCancellationService cancellationService;
    @MockitoBean private BookingQueryService queryService;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter; // mock security filter to let request through if needed
    @MockitoBean private java.time.Clock clock;

    @Test
    void create_nullHoldId_returnsBadRequestWithProblemDetail() throws Exception {
        // Missing holdId which is @NotNull
        String json = "{\"discountCode\":\"CODE\"}";

        // Note: we might get 401/403 if security is active, but we can bypass or assume it returns 400 for validation if not fully secured or if we mock it correctly.
        // If security blocks it, @WebMvcTest might require @WithMockUser or similar, but since we are just checking validation shape:
        mockMvc.perform(post("/api/v1/bookings")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                // Expect 400 or at least check the shape. If 401 is thrown because of security, we might need to add @WithMockUser.
                // Assuming standard setup, let's just assert status 400.
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").exists());
    }
}
