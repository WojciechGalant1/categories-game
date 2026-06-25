package com.example.panstwamiasta.service;

import com.example.panstwamiasta.dto.CreateRoomRequest;
import com.example.panstwamiasta.dto.JoinResponse;
import com.example.panstwamiasta.dto.JoinRoomRequest;
import com.example.panstwamiasta.dto.SubmitAnswersRequest;
import com.example.panstwamiasta.dto.SubmitVoteRequest;
import com.example.panstwamiasta.room.Room;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("test")
class CalculateScoresTest {

    @Autowired
    private RoomService roomService;

    @Test
    void onlyValidAnswer_gets15Points_whenOthersRejected() {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest1 = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest1"));
        JoinResponse guest2 = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest2"));
        String code = host.getCode();

        roomService.startGame(code, host.getPlayerId());

        roomService.submitAnswers(code, host.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "Polska")));
        roomService.submitAnswers(code, guest1.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "bad1")));
        roomService.submitAnswers(code, guest2.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "bad2")));

        roomService.triggerStop(code, host.getPlayerId());
        roomService.triggerStop(code, guest1.getPlayerId());
        roomService.triggerStop(code, guest2.getPlayerId());

        String category = "Państwa";
        roomService.submitVote(code, host.getPlayerId(),
                new SubmitVoteRequest(guest1.getPlayerId(), category, false));
        roomService.submitVote(code, host.getPlayerId(),
                new SubmitVoteRequest(guest2.getPlayerId(), category, false));

        roomService.submitVote(code, guest1.getPlayerId(),
                new SubmitVoteRequest(host.getPlayerId(), category, true));
        roomService.submitVote(code, guest1.getPlayerId(),
                new SubmitVoteRequest(guest2.getPlayerId(), category, false));

        roomService.submitVote(code, guest2.getPlayerId(),
                new SubmitVoteRequest(host.getPlayerId(), category, true));
        roomService.submitVote(code, guest2.getPlayerId(),
                new SubmitVoteRequest(guest1.getPlayerId(), category, false));

        roomService.nextRound(code, host.getPlayerId());

        Room room = roomService.getRoom(code);
        assertEquals(15, room.getScores().get(host.getPlayerId().toString()));
        assertEquals(0, room.getScores().get(guest1.getPlayerId().toString()));
        assertEquals(0, room.getScores().get(guest2.getPlayerId().toString()));
    }

    @Test
    void twoUniqueValidAnswers_get10PointsEach() {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest"));
        String code = host.getCode();

        roomService.startGame(code, host.getPlayerId());

        roomService.submitAnswers(code, host.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "Polska")));
        roomService.submitAnswers(code, guest.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "Niemcy")));

        roomService.triggerStop(code, host.getPlayerId());
        roomService.triggerStop(code, guest.getPlayerId());

        String category = "Państwa";
        roomService.submitVote(code, host.getPlayerId(),
                new SubmitVoteRequest(guest.getPlayerId(), category, true));
        roomService.submitVote(code, guest.getPlayerId(),
                new SubmitVoteRequest(host.getPlayerId(), category, true));

        roomService.nextRound(code, host.getPlayerId());

        Room room = roomService.getRoom(code);
        assertEquals(10, room.getScores().get(host.getPlayerId().toString()));
        assertEquals(10, room.getScores().get(guest.getPlayerId().toString()));
    }

    @Test
    void duplicateValidAnswers_get5PointsEach() {
        JoinResponse host = roomService.createRoom(new CreateRoomRequest("Host", false));
        JoinResponse guest = roomService.joinRoom(host.getCode(), new JoinRoomRequest("Guest"));
        String code = host.getCode();

        roomService.startGame(code, host.getPlayerId());

        roomService.submitAnswers(code, host.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "Polska")));
        roomService.submitAnswers(code, guest.getPlayerId(),
                new SubmitAnswersRequest(Map.of("Państwa", "Polska")));

        roomService.triggerStop(code, host.getPlayerId());
        roomService.triggerStop(code, guest.getPlayerId());

        String category = "Państwa";
        roomService.submitVote(code, host.getPlayerId(),
                new SubmitVoteRequest(guest.getPlayerId(), category, true));
        roomService.submitVote(code, guest.getPlayerId(),
                new SubmitVoteRequest(host.getPlayerId(), category, true));

        roomService.nextRound(code, host.getPlayerId());

        Room room = roomService.getRoom(code);
        assertEquals(5, room.getScores().get(host.getPlayerId().toString()));
        assertEquals(5, room.getScores().get(guest.getPlayerId().toString()));
    }
}
