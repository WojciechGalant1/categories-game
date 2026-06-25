package com.example.panstwamiasta.service;

import com.example.panstwamiasta.room.Room;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pure scoring rules for a completed round (answers + votes → score deltas).
 * No persistence or Spring dependencies — easy to unit test in isolation.
 */
public final class ScoreCalculator {

    private ScoreCalculator() {}

    public static void apply(Room room) {
        Map<String, Map<String, String>> answers = room.getGame().getAnswers();
        Map<String, Map<String, Map<String, Boolean>>> votes = room.getGame().getVotes();
        List<String> categories = room.getSettings().getCategories();

        for (Map.Entry<String, Map<String, String>> playerEntry : answers.entrySet()) {
            String playerId = playerEntry.getKey();
            Map<String, String> playerAnswers = playerEntry.getValue();

            for (String category : categories) {
                String answer = playerAnswers.get(category);
                if (answer == null || answer.trim().isEmpty()) {
                    continue;
                }

                if (!isAnswerValid(votes, playerId, category)) {
                    continue;
                }

                int sameAnswerCount = 0;
                boolean someoneElseHasValidAnswer = false;

                for (Map.Entry<String, Map<String, String>> otherEntry : answers.entrySet()) {
                    String otherId = otherEntry.getKey();
                    if (otherId.equals(playerId)) {
                        continue;
                    }

                    String otherAnswer = otherEntry.getValue().get(category);
                    if (otherAnswer == null || otherAnswer.trim().isEmpty()) {
                        continue;
                    }

                    if (!isAnswerValid(votes, otherId, category)) {
                        continue;
                    }

                    someoneElseHasValidAnswer = true;
                    if (otherAnswer.trim().toLowerCase().equals(answer.trim().toLowerCase())) {
                        sameAnswerCount++;
                    }
                }

                int points;
                if (!someoneElseHasValidAnswer) {
                    points = 15;
                } else if (sameAnswerCount == 0) {
                    points = 10;
                } else {
                    points = 5;
                }

                room.getScores().put(playerId, room.getScores().get(playerId) + points);
            }
        }
    }

    private static boolean isAnswerValid(
            Map<String, Map<String, Map<String, Boolean>>> votes,
            String playerId,
            String category) {
        Map<String, Boolean> categoryVotes = votes.getOrDefault(playerId, Map.of()).get(category);
        if (categoryVotes == null) {
            return true;
        }
        long validVotes = categoryVotes.values().stream().filter(v -> v).count();
        long invalidVotes = categoryVotes.values().stream().filter(v -> !v).count();
        return validVotes >= invalidVotes;
    }
}
