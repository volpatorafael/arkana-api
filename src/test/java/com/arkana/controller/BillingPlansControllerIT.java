package com.arkana.controller;

import com.arkana.domain.Profile;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class BillingPlansControllerIT extends BaseControllerIT {

    @Test
    void listsThePublicSubscriptionPlanResponse() throws Exception {
        String anonymousResponse = mockMvcPerform(get("/v1/public/plans"))
            .andExpect(status().isOk())
            .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
            .andExpect(header().string(
                "Cache-Control",
                "public, max-age=60, s-maxage=60, stale-while-revalidate=60"))
            .andExpect(jsonPath("$", hasSize(2)))
            .andExpect(jsonPath("$[0].id").isNotEmpty())
            .andExpect(jsonPath("$[0].code").isString())
            .andExpect(jsonPath("$[0].name").isString())
            .andExpect(jsonPath("$[0].interval").value("MONTH"))
            .andExpect(jsonPath("$[0].amount").isNumber())
            .andExpect(jsonPath("$[0].currency").value("BRL"))
            .andExpect(jsonPath("$[0].trialDays").value(14))
            .andExpect(jsonPath("$[0].availablePaymentMethods[0]").value("PIX_AUTOMATIC"))
            .andExpect(jsonPath("$[1].id").isNotEmpty())
            .andExpect(jsonPath("$[1].code").isString())
            .andExpect(jsonPath("$[1].name").isString())
            .andExpect(jsonPath("$[1].interval").value("YEAR"))
            .andExpect(jsonPath("$[1].amount").isNumber())
            .andExpect(jsonPath("$[1].currency").value("BRL"))
            .andExpect(jsonPath("$[1].trialDays").value(14))
            .andExpect(jsonPath("$[1].availablePaymentMethods[0]").value("PIX_AUTOMATIC"))
            .andReturn()
            .getResponse()
            .getContentAsString();

        Profile user = entityGeneratorService.randomProfile();
        String authenticatedResponse = mockMvcPerform(get("/v1/public/plans").with(authenticatedAs(user)))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsString();
        assertThat(authenticatedResponse).isEqualTo(anonymousResponse);
    }
}
