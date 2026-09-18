package com.cineagent.pricing.api;

import com.cineagent.pricing.repository.DiscountCodeRepository;
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

@WebMvcTest(controllers = AdminDiscountController.class)
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandler.class)
class AdminDiscountControllerValidationTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private DiscountCodeRepository repository;
    @MockitoBean private JwtAuthenticationFilter jwtAuthenticationFilter;
    @MockitoBean private java.time.Clock clock;

    @Test
    void create_invalidRequest_returnsBadRequestWithProblemDetail() throws Exception {
        // Missing many @NotNull fields, like discountType, validFrom, validUntil
        String json = "{\"code\":\"\", \"value\":-5.00}"; // code is blank, value < 0.01

        mockMvc.perform(post("/api/v1/admin/discounts")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.status").value(400))
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.title").exists());
    }
}
