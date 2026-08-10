package com.arkana.controller;

import com.arkana.BaseIT;
import com.arkana.domain.BillingPaymentMethod;
import com.arkana.domain.Profile;
import com.arkana.integration.PaymentProvider;
import com.arkana.integration.efi.EfiProvider;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
public abstract class BaseControllerIT extends BaseIT {

    @MockitoBean(name = "abacatePayProvider")
    protected PaymentProvider abacatePayProvider;

    @MockitoBean(name = "asaasProvider")
    protected PaymentProvider asaasProvider;
    @MockitoBean(name = "efiProvider")
    protected EfiProvider efiProvider;

    @Autowired
    private MockMvc mockMvc;

    @BeforeEach
    protected void configurePaymentProviders() {
        when(abacatePayProvider.supportedPaymentMethods())
            .thenReturn(java.util.Set.of(BillingPaymentMethod.PIX_AUTOMATIC, BillingPaymentMethod.CARD));
        when(abacatePayProvider.requiresPlanMapping()).thenReturn(true);
        when(abacatePayProvider.requiresPlanMapping(org.mockito.ArgumentMatchers.any())).thenReturn(true);
        when(asaasProvider.supportedPaymentMethods()).thenReturn(java.util.Set.of(BillingPaymentMethod.CARD));
        when(asaasProvider.requiresPlanMapping()).thenReturn(false);
        when(asaasProvider.requiresPlanMapping(org.mockito.ArgumentMatchers.any())).thenReturn(false);
        when(asaasProvider.supportsDeferredFirstCharge()).thenReturn(true);
        when(efiProvider.supportedPaymentMethods())
            .thenReturn(java.util.Set.of(BillingPaymentMethod.PIX_AUTOMATIC, BillingPaymentMethod.CARD));
        when(efiProvider.requiresPlanMapping(BillingPaymentMethod.CARD)).thenReturn(true);
        when(efiProvider.requiresPlanMapping(BillingPaymentMethod.PIX_AUTOMATIC)).thenReturn(false);
        when(efiProvider.supportsDeferredFirstCharge()).thenReturn(true);
    }

    protected final ResultActions mockMvcPerform(MockHttpServletRequestBuilder requestBuilder) throws Exception {
        return mockMvc.perform(requestBuilder);
    }

    protected final RequestPostProcessor authenticatedAs(Profile user) {
        return jwt().jwt(token -> token
            .subject(user.getId().toString())
            .claim("aud", "authenticated")
            .claim("email", user.getEmail()));
    }

    protected final ResultActions expectNotFound(ResultActions result, String detail) throws Exception {
        return result
            .andExpect(status().isNotFound())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
            .andExpect(jsonPath("$.detail").value(detail));
    }
}
