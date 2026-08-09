package com.arkana.controller;

import com.arkana.BaseIT;
import com.arkana.domain.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@AutoConfigureMockMvc
@Transactional
public abstract class BaseControllerIT extends BaseIT {

    @Autowired
    private MockMvc mockMvc;

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
