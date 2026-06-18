package com.example.panstwamiasta.controller;

import com.example.panstwamiasta.dto.*;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.service.RoomService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/settings")
    public ResponseEntity<?> updateSettings(@PathVariable String code, @RequestBody UpdateSettingsRequest request) {
        try {
            roomService.updateSettings(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Only host can change settings")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/start")
    public ResponseEntity<?> startGame(@PathVariable String code, @RequestBody PlayerIdRequest request) {
        try {
            roomService.startGame(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Only host can start game")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/stop")
    public ResponseEntity<?> triggerStop(@PathVariable String code, @RequestBody PlayerIdRequest request) {
        try {
            roomService.triggerStop(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Player not in room")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/answers")
    public ResponseEntity<?> submitAnswers(@PathVariable String code, @RequestBody SubmitAnswersRequest request) {
        try {
            roomService.submitAnswers(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/vote")
    public ResponseEntity<?> submitVote(@PathVariable String code, @RequestBody SubmitVoteRequest request) {
        try {
            roomService.submitVote(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Voter not in room")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/next-round")
    public ResponseEntity<?> nextRound(@PathVariable String code, @RequestBody PlayerIdRequest request) {
        try {
            roomService.nextRound(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Only host can end the round")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/leave")
    public ResponseEntity<?> leaveRoom(@PathVariable String code, @RequestBody PlayerIdRequest request) {
        try {
            roomService.leaveRoom(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Player not in room")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }

    @PostMapping("/rooms/{code}/reset")
    public ResponseEntity<?> resetToLobby(@PathVariable String code, @RequestBody PlayerIdRequest request) {
        try {
            roomService.resetToLobby(code, request);
            return ResponseEntity.ok(new SuccessResponse(true));
        } catch (RuntimeException e) {
            if (e.getMessage().equals("Room not found")) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(e.getMessage()));
            } else if (e.getMessage().equals("Only host can reset")) {
                return ResponseEntity.status(HttpStatus.FORBIDDEN).body(new ApiError(e.getMessage()));
            }
            return ResponseEntity.badRequest().body(new ApiError(e.getMessage()));
        }
    }
}
