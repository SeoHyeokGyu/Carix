package com.carix.pipeline.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.ResponseEntity;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

class AdminKeyInterceptorTest {

  private MockMvc mockMvc;

  @RestController
  @RequestMapping("/internal/pipeline")
  static class DummyPipelineController {
    @GetMapping("/test")
    public ResponseEntity<String> testEndpoint() {
      return ResponseEntity.ok("success");
    }
  }

  @BeforeEach
  void setUp() {
    AdminKeyInterceptor interceptor = new AdminKeyInterceptor("test-secret-key");
    mockMvc =
        MockMvcBuilders.standaloneSetup(new DummyPipelineController())
            .addInterceptors(interceptor)
            .build();
  }

  @Test
  @DisplayName("X-Admin-Key 헤더가 없으면 403 Forbidden 반환")
  void missingHeaderReturnsForbidden() throws Exception {
    mockMvc
        .perform(get("/internal/pipeline/test"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("FORBIDDEN")));
  }

  @Test
  @DisplayName("X-Admin-Key 헤더가 불일치하면 403 Forbidden 반환")
  void wrongHeaderReturnsForbidden() throws Exception {
    mockMvc
        .perform(get("/internal/pipeline/test").header("X-Admin-Key", "wrong-key"))
        .andExpect(status().isForbidden())
        .andExpect(content().string(org.hamcrest.Matchers.containsString("FORBIDDEN")));
  }

  @Test
  @DisplayName("X-Admin-Key 헤더가 일치하면 200 OK 통과")
  void correctHeaderPasses() throws Exception {
    mockMvc
        .perform(get("/internal/pipeline/test").header("X-Admin-Key", "test-secret-key"))
        .andExpect(status().isOk())
        .andExpect(content().string("success"));
  }

  @Test
  @DisplayName("OPTIONS 요청은 인증 없이 통과 (CORS Preflight)")
  void optionsRequestPasses() throws Exception {
    mockMvc.perform(options("/internal/pipeline/test")).andExpect(status().isOk());
  }
}
