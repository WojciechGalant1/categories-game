package com.example.panstwamiasta.controller;

import com.example.panstwamiasta.auth.PlayerSessionTokenService;
import com.example.panstwamiasta.dto.CreateRoomRequest;
import com.example.panstwamiasta.dto.JoinResponse;
import com.example.panstwamiasta.dto.JoinRoomRequest;
import com.example.panstwamiasta.service.RoomService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class RoomControllerSecurityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private RoomService roomService;

    @Autowired
    private PlayerSessionTokenService tokenService;

    @Test
    void createRoom_returnsAccessToken() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/rooms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"Host\",\"isPublic\":false}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertNotNull(body.get("accessToken").asText());
        assertNotNull(body.get("playerId").asText());
        assertNotNull(body.get("code").asText());
    }

    @Test
    void startAsGuest_returns403() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest"));

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/start")
                        .header("Authorization", "Bearer " + guest.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void startAsHost_returns200() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/start")
                        .header("Authorization", "Bearer " + host.getAccessToken())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void rejoin_sameNick_inLobby_returnsNewTokenSamePlayerId() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Marek", false));

        MvcResult result = mockMvc.perform(post("/api/rooms/" + host.getCode() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"Marek\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(host.getPlayerId().toString(), body.get("playerId").asText());
        assertNotNull(body.get("accessToken").asText());
    }

    @Test
    void join_newNick_inPlaying_returns403() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        roomService.startGame(host.getCode(), host.getPlayerId());

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"NewPlayer\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void rejoin_sameNick_inPlaying_returnsNewToken() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest"));
        roomService.startGame(host.getCode(), host.getPlayerId());

        MvcResult result = mockMvc.perform(post("/api/rooms/" + host.getCode() + "/join")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nick\":\"Guest\"}"))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString());
        assertEquals(guest.getPlayerId().toString(), body.get("playerId").asText());
        assertNotNull(body.get("accessToken").asText());
    }

    @Test
    void hostTransfer_newHostCanStart() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest"));

        roomService.leaveRoom(host.getCode(), host.getPlayerId());

        String newHostToken = tokenService.issueToken(guest.getPlayerId(), host.getCode(), "Guest", false);

        mockMvc.perform(post("/api/rooms/" + host.getCode() + "/start")
                        .header("Authorization", "Bearer " + newHostToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isOk());
    }

    @Test
    void getRoom_withoutToken_returns401() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));

        mockMvc.perform(get("/api/rooms/" + host.getCode()))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void getRoom_withMemberToken_returns200() throws Exception {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));

        mockMvc.perform(get("/api/rooms/" + host.getCode())
                        .header("Authorization", "Bearer " + host.getAccessToken()))
                .andExpect(status().isOk());
    }
}
