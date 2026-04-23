package com.dong.ddrag.qa;

import com.dong.ddrag.auth.security.JwtAccessTokenService;
import com.dong.ddrag.common.exception.GlobalExceptionHandler;
import com.dong.ddrag.qa.controller.QaController;
import com.dong.ddrag.qa.service.QaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(QaController.class)
@Import(GlobalExceptionHandler.class)
class QaControllerErrorResponseTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private QaService qaService;

    @MockBean
    private JwtAccessTokenService jwtAccessTokenService;

    @Test
    void shouldReturnApiResponseForValidationFailure() throws Exception {
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"groupId":2001,"question":" "}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("问题不能为空"));

        verifyNoInteractions(qaService);
    }

    @Test
    void shouldReturnApiResponseForMalformedJson() throws Exception {
        mockMvc.perform(post("/api/qa/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("请求体格式非法"));

        verifyNoInteractions(qaService);
    }
}
