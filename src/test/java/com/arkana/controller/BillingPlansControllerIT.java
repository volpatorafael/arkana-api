package com.arkana.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class BillingPlansControllerIT {
  @Autowired
  MockMvc mvc;

  @Test
  void listsThePublicSubscriptionPlanResponse() throws Exception {
    mvc.perform(get("/v1/public/plans"))
        .andExpect(status().isOk())
        .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
        .andExpect(header().string("Cache-Control", "public, max-age=60, s-maxage=60, stale-while-revalidate=60"))
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
        .andExpect(jsonPath("$[1].availablePaymentMethods[0]").value("PIX_AUTOMATIC"));
  }
}
