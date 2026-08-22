package com.carix.demo;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
// Boot 4에서 org.springframework.test.web.servlet.autoconfigure 로부터 이 패키지로 옮겨졌다.
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class EchoControllerTest {

  @Autowired private MockMvc mockMvc;

  @Test
  void 입력한_문자열을_그대로_돌려준다() throws Exception {
    mockMvc
        .perform(
            post("/v1/demo/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"안녕 Carix\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.message").value("안녕 Carix"))
        .andExpect(jsonPath("$.receivedAt").exists());
  }

  @Test
  void 빈_문자열이면_공통_에러_포맷으로_400을_반환한다() throws Exception {
    mockMvc
        .perform(
            post("/v1/demo/echo")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"message\":\"  \"}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"))
        .andExpect(jsonPath("$.timestamp").exists())
        .andExpect(jsonPath("$.details.message").exists());
  }
}
