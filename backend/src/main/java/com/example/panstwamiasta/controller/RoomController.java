package com.example.panstwamiasta.controller;

import com.example.panstwamiasta.auth.PlayerPrincipal;
import com.example.panstwamiasta.dto.*;
import com.example.panstwamiasta.exception.RoomNotFoundException;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.service.RoomService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class RoomController {

    @Autowired
    private RoomService roomService;

    @GetMapping("/rooms")
    public ResponseEntity<List<PublicRoom>> listPublicRooms() {
        return ResponseEntity.ok(roomService.listPublicRooms());
    }

    @PostMapping("/rooms")
    public ResponseEntity<JoinResponse> createRoom(@Valid @RequestBody CreateRoomRequest request) {
        return ResponseEntity.ok(roomService.createRoom(request));
    }

    @GetMapping("/rooms/{code}")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<Room> getRoomState(@PathVariable String code) {
        Room room = roomService.getRoom(code);
        if (room == null) {
            throw new RoomNotFoundException();
        }
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{code}/join")
    public ResponseEntity<JoinResponse> joinRoom(@PathVariable String code,
                                                 @Valid @RequestBody JoinRoomRequest request) {
        return ResponseEntity.ok(roomService.joinRoom(code, request));
    }

    @PostMapping("/rooms/{code}/settings")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<SuccessResponse> updateSettings(@PathVariable String code,
                                                          @AuthenticationPrincipal PlayerPrincipal principal,
                                                          @Valid @RequestBody UpdateSettingsRequest request) {
        roomService.updateSettings(code, principal.playerId(), request);
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/start")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<SuccessResponse> startGame(@PathVariable String code,
                                                     @AuthenticationPrincipal PlayerPrincipal principal) {
        roomService.startGame(code, principal.playerId());
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/stop")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<SuccessResponse> triggerStop(@PathVariable String code,
                                                       @AuthenticationPrincipal PlayerPrincipal principal) {
        roomService.triggerStop(code, principal.playerId());
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/answers")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<SuccessResponse> submitAnswers(@PathVariable String code,
                                                         @AuthenticationPrincipal PlayerPrincipal principal,
                                                         @Valid @RequestBody SubmitAnswersRequest request) {
        roomService.submitAnswers(code, principal.playerId(), request);
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/vote")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<SuccessResponse> submitVote(@PathVariable String code,
                                                      @AuthenticationPrincipal PlayerPrincipal principal,
                                                      @Valid @RequestBody SubmitVoteRequest request) {
        roomService.submitVote(code, principal.playerId(), request);
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/next-round")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<SuccessResponse> nextRound(@PathVariable String code,
                                                     @AuthenticationPrincipal PlayerPrincipal principal) {
        roomService.nextRound(code, principal.playerId());
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/leave")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<SuccessResponse> leaveRoom(@PathVariable String code,
                                                     @AuthenticationPrincipal PlayerPrincipal principal) {
        roomService.leaveRoom(code, principal.playerId());
        return ResponseEntity.ok(new SuccessResponse(true));
    }

    @PostMapping("/rooms/{code}/reset")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<SuccessResponse> resetToLobby(@PathVariable String code,
                                                        @AuthenticationPrincipal PlayerPrincipal principal) {
        roomService.resetToLobby(code, principal.playerId());
        return ResponseEntity.ok(new SuccessResponse(true));
    }
}
