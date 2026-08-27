package com.carix.authservice.invitation;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.carix.authservice.config.InternalApiKeyFilter;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = InvitationController.class)
@TestPropertySource(properties = "carix.auth.internal-api-key=" + InvitationControllerTest.KEY)
class InvitationControllerTest {

  static final String KEY = "test-internal-key";

  @Autowired private MockMvc mockMvc;

  @MockitoBean private InvitationService invitationService;

  @Test
  void 유효한_키로_초대를_등록하면_201과_등록_결과를_돌려준다() throws Exception {
    given(invitationService.register(anyList()))
        .willReturn(
            new InvitationRegistrationResponse(List.of("new@example.com"), List.of("old@example.com")));

    mockMvc
        .perform(
            post("/internal/invitations")
                .header(InternalApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"new@example.com\",\"old@example.com\"]}"))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.created[0]").value("new@example.com"))
        .andExpect(jsonPath("$.skipped[0]").value("old@example.com"));
  }

  @Test
  void 키가_없으면_403이고_서비스는_호출되지_않는다() throws Exception {
    mockMvc
        .perform(
            post("/internal/invitations")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"new@example.com\"]}"))
        .andExpect(status().isForbidden());

    verify(invitationService, never()).register(anyList());
  }

  @Test
  void 키가_틀리면_403이다() throws Exception {
    mockMvc
        .perform(
            post("/internal/invitations")
                .header(InternalApiKeyFilter.HEADER, "wrong-key")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"new@example.com\"]}"))
        .andExpect(status().isForbidden());
  }

  @Test
  void 이메일_형식이_틀리면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/internal/invitations")
                .header(InternalApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[\"not-an-email\"]}"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.code").value("INVALID_INPUT"));
  }

  @Test
  void 빈_목록이면_400이다() throws Exception {
    mockMvc
        .perform(
            post("/internal/invitations")
                .header(InternalApiKeyFilter.HEADER, KEY)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"emails\":[]}"))
        .andExpect(status().isBadRequest());
  }

  @Test
  void 초대_목록을_조회한다() throws Exception {
    OffsetDateTime now = OffsetDateTime.now();
    given(invitationService.findAll())
        .willReturn(List.of(new InvitationSummary("beta@example.com", "PENDING", now, now)));

    mockMvc
        .perform(get("/internal/invitations").header(InternalApiKeyFilter.HEADER, KEY))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].email").value("beta@example.com"))
        .andExpect(jsonPath("$[0].status").value("PENDING"));
  }
}
