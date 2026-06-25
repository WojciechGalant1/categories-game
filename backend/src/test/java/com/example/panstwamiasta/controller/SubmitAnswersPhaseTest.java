package com.example.panstwamiasta.controller;

import com.example.panstwamiasta.dto.CreateRoomRequest;
import com.example.panstwamiasta.dto.JoinResponse;
import com.example.panstwamiasta.service.RoomService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SubmitAnswersPhaseTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private RoomService roomService;

    @Test
    void submitAnswers_inPlaying_succeeds() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        roomService.startGame(host.getCode(), host.getPlayerId());

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/answers")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Państwa\":\"Polska\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void submitAnswers_inReviewing_firstSubmit_succeeds() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        roomService.startGame(host.getCode(), host.getPlayerId());
        roomService.triggerStop(host.getCode(), host.getPlayerId());

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/answers")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Państwa\":\"Polska\"}}"))
                .andExpect(status().isOk());
    }

    @Test
    void submitAnswers_inReviewing_overwrite_returns400() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        String code = host.getCode();
        roomService.startGame(code, host.getPlayerId());

        mockMvc.perform(post("/api/rooms/" + code + "/answers")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Państwa\":\"Polska\"}}"))
                .andExpect(status().isOk());

        roomService.triggerStop(code, host.getPlayerId());

        mockMvc.perform(post("/api/rooms/" + code + "/answers")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Państwa\":\"Niemcy\"}}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void submitAnswers_inLobby_returns400() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/answers")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"answers\":{\"Państwa\":\"Polska\"}}"))
                .andExpect(status().isBadRequest());
    }
}
