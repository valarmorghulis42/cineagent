package com.cineagent.refund.service;

import com.cineagent.refund.domain.RefundPolicy;
import com.cineagent.refund.domain.RefundScopeType;
import com.cineagent.refund.repository.RefundPolicyRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundPolicyResolverTest {

    @Mock private RefundPolicyRepository repository;
    
    private RefundPolicyResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RefundPolicyResolver(repository);
    }

    @Test
    void resolvePercentage_mostSpecificScopeWins() {
        // Theater policy
        RefundPolicy theater = mock(RefundPolicy.class);
        when(theater.getMinMinutesBeforeShow()).thenReturn(60);
        when(theater.getRefundPercentage()).thenReturn(new BigDecimal("75.00"));

        when(repository.findActiveByScope(RefundScopeType.SHOW, 1L)).thenReturn(List.of());
        when(repository.findActiveByScope(RefundScopeType.THEATER, 2L)).thenReturn(List.of(theater));
        // Should not query CITY or GLOBAL once THEATER matches
        
        BigDecimal percentage = resolver.resolvePercentage(1L, 2L, 3L, 120);
        
        assertThat(percentage).isEqualByComparingTo("75.00");
        verify(repository, never()).findActiveByScope(RefundScopeType.CITY, 3L);
        verify(repository, never()).findActiveByScope(RefundScopeType.GLOBAL, null);
    }

    @Test
    void resolvePercentage_exactBoundaryCondition() {
        RefundPolicy global = mock(RefundPolicy.class);
        when(global.getMinMinutesBeforeShow()).thenReturn(120);
        when(global.getRefundPercentage()).thenReturn(new BigDecimal("100.00"));
        
        when(repository.findActiveByScope(RefundScopeType.SHOW, 1L)).thenReturn(List.of());
        when(repository.findActiveByScope(RefundScopeType.THEATER, 2L)).thenReturn(List.of());
        when(repository.findActiveByScope(RefundScopeType.CITY, 3L)).thenReturn(List.of());
        when(repository.findActiveByScope(RefundScopeType.GLOBAL, null)).thenReturn(List.of(global));

        // Exact match at minMinutesBeforeShow
        BigDecimal percentage = resolver.resolvePercentage(1L, 2L, 3L, 120);
        
        assertThat(percentage).isEqualByComparingTo("100.00");
    }

    @Test
    void resolvePercentage_belowEveryTier_returnsZeroFloor() {
        RefundPolicy global = mock(RefundPolicy.class);
        when(global.getMinMinutesBeforeShow()).thenReturn(120);
        
        when(repository.findActiveByScope(any(RefundScopeType.class), any())).thenAnswer(inv -> {
            RefundScopeType type = inv.getArgument(0);
            if (type == RefundScopeType.GLOBAL) return List.of(global);
            return List.of();
        });

        // 60 minutes before show is less than 120
        BigDecimal percentage = resolver.resolvePercentage(1L, 2L, 3L, 60);
        
        assertThat(percentage).isEqualByComparingTo("0.00");
    }
}
