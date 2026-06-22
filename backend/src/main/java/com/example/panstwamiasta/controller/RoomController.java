package com.example.panstwamiasta.controller;

import com.example.panstwamiasta.auth.PlayerPrincipal;
import com.example.panstwamiasta.dto.*;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
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
    public ResponseEntity<?> createRoom(@RequestBody CreateRoomRequest request) {
        if (request.getNick() == null || request.getNick().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("Nick is required"));
        }
        try {
            JoinResponse response = roomService.createRoom(request);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(e.getMessage()));
        }
    }

    @GetMapping("/rooms/{code}")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<?> getRoomState(@PathVariable String code) {
        Room room = roomService.getRoom(code);
        if (room == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError("Room not found"));
        }
        return ResponseEntity.ok(room);
    }

    @PostMapping("/rooms/{code}/join")
    public ResponseEntity<?> joinRoom(@PathVariable String code, @RequestBody JoinRoomRequest request) {
        if (request.getNick() == null || request.getNick().trim().isEmpty()) {
            return ResponseEntity.badRequest().body(new ApiError("Nick is required"));
        }
        try {
            JoinResponse response = roomService.joinRoom(code, request);
            return ResponseEntity.ok(response);
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            if (e.getMessage().equals("Game already started")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/settings")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<?> updateSettings(@PathVariable String code,
                                            @AuthenticationPrincipal PlayerPrincipal principal,
                                            @RequestBody UpdateSettingsRequest request) {
        try {
            roomService.updateSettings(code, principal.playerId(), request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/start")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<?> startGame(@PathVariable String code,
                                       @AuthenticationPrincipal PlayerPrincipal principal) {
        try {
            roomService.startGame(code, principal.playerId());
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/stop")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<?> triggerStop(@PathVariable String code,
                                         @AuthenticationPrincipal PlayerPrincipal principal) {
        try {
            roomService.triggerStop(code, principal.playerId());
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/answers")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<?> submitAnswers(@PathVariable String code,
                                           @AuthenticationPrincipal PlayerPrincipal principal,
                                           @RequestBody SubmitAnswersRequest request) {
        try {
            roomService.submitAnswers(code, principal.playerId(), request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/vote")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<?> submitVote(@PathVariable String code,
                                        @AuthenticationPrincipal PlayerPrincipal principal,
                                        @RequestBody SubmitVoteRequest request) {
        try {
            roomService.submitVote(code, principal.playerId(), request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/next-round")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<?> nextRound(@PathVariable String code,
                                       @AuthenticationPrincipal PlayerPrincipal principal) {
        try {
            roomService.nextRound(code, principal.playerId());
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/leave")
    @PreAuthorize("@roomAuth.isMember(#code, authentication)")
    public ResponseEntity<?> leaveRoom(@PathVariable String code,
                                       @AuthenticationPrincipal PlayerPrincipal principal) {
        try {
            roomService.leaveRoom(code, principal.playerId());
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/reset")
    @PreAuthorize("@roomAuth.isHost(#code, authentication)")
    public ResponseEntity<?> resetToLobby(@PathVariable String code,
                                          @AuthenticationPrincipal PlayerPrincipal principal) {
        try {
            roomService.resetToLobby(code, principal.playerId());
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
}
