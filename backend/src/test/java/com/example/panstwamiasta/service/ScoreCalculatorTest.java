package com.example.panstwamiasta.service;

import com.example.panstwamiasta.model.GameState;
import com.example.panstwamiasta.model.RoomSettings;
import com.example.panstwamiasta.room.Room;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ScoreCalculatorTest {

    @Test
    void onlyValidAnswer_gets15Points_whenOthersRejected() {
        String host = "host-id";
        String guest1 = "guest1-id";
        String guest2 = "guest2-id";
        String category = "Państwa";

        Map<String, Map<String, String>> answers = Map.of(
                host, Map.of(category, "Polska"),
                guest1, Map.of(category, "bad1"),
                guest2, Map.of(category, "bad2")
        );

        Map<String, Map<String, Map<String, Boolean>>> votes = new HashMap<>();
        votes.put(guest1, Map.of(category, Map.of(host, false, guest2, false)));
        votes.put(guest2, Map.of(category, Map.of(host, false, guest1, false)));
        votes.put(host, Map.of(category, Map.of(guest1, true, guest2, true)));

        Room room = roomWithScores(
                answers,
                votes,
                List.of(category),
                Map.of(host, 0, guest1, 0, guest2, 0)
        );

        ScoreCalculator.apply(room);

        assertEquals(15, room.getScores().get(host));
        assertEquals(0, room.getScores().get(guest1));
        assertEquals(0, room.getScores().get(guest2));
    }

    @Test
    void twoUniqueValidAnswers_get10PointsEach() {
        String host = "host-id";
        String guest = "guest-id";
        String category = "Państwa";

        Map<String, Map<String, String>> answers = Map.of(
                host, Map.of(category, "Polska"),
                guest, Map.of(category, "Niemcy")
        );

        Map<String, Map<String, Map<String, Boolean>>> votes = new HashMap<>();
        votes.put(host, Map.of(category, Map.of(guest, true)));
        votes.put(guest, Map.of(category, Map.of(host, true)));

        Room room = roomWithScores(
                answers,
                votes,
                List.of(category),
                Map.of(host, 0, guest, 0)
        );

        ScoreCalculator.apply(room);

        assertEquals(10, room.getScores().get(host));
        assertEquals(10, room.getScores().get(guest));
    }

    @Test
    void duplicateValidAnswers_get5PointsEach() {
        String host = "host-id";
        String guest = "guest-id";
        String category = "Państwa";

        Map<String, Map<String, String>> answers = Map.of(
                host, Map.of(category, "Polska"),
                guest, Map.of(category, "Polska")
        );

        Map<String, Map<String, Map<String, Boolean>>> votes = new HashMap<>();
        votes.put(host, Map.of(category, Map.of(guest, true)));
        votes.put(guest, Map.of(category, Map.of(host, true)));

        Room room = roomWithScores(
                answers,
                votes,
                List.of(category),
                Map.of(host, 0, guest, 0)
        );

        ScoreCalculator.apply(room);

        assertEquals(5, room.getScores().get(host));
        assertEquals(5, room.getScores().get(guest));
    }

    private static Room roomWithScores(
            Map<String, Map<String, String>> answers,
            Map<String, Map<String, Map<String, Boolean>>> votes,
            List<String> categories,
            Map<String, Integer> scores) {
        GameState game = new GameState(
                1, "A", null, null,
                new HashMap<>(answers),
                new HashMap<>(votes),
                new ArrayList<>(), 0, 0
        );
        RoomSettings settings = new RoomSettings(categories, 1, 5, 8);
        return new Room(
                "TEST01",
                false,
                Room.RoomStatus.reviewing,
                List.of(),
                new HashMap<>(scores),
                settings,
                game
        );
    }
}
