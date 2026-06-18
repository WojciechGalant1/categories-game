package com.example.panstwamiasta.service;

import com.example.panstwamiasta.dto.*;
import com.example.panstwamiasta.model.GameState;
import com.example.panstwamiasta.model.Player;
import com.example.panstwamiasta.model.RoomSettings;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import jakarta.transaction.Transactional;

import java.util.*;

@Service
public class RoomService {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    @Lazy
    private RoomBroadcastService roomBroadcastService;

    private final Random random = new Random();

    private Room saveAndNotify(Room room) {
        Room saved = roomRepository.save(room);
        String code = saved.getCode();
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    roomBroadcastService.publishRoomUpdate(code);
                }
            });
        } else {
            roomBroadcastService.publishRoomUpdate(code);
        }
        return saved;
    }

    // Generate a random 6-character alphanumeric code
    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

    // Create a new room
    @Transactional
    public JoinResponse createRoom(CreateRoomRequest request) {
        String code = generateRoomCode();
        while (roomRepository.findById(code).isPresent()) {
            code = generateRoomCode();
        }

        UUID playerId = UUID.randomUUID();
        Player host = new Player(playerId, request.getNick(), true);

        RoomSettings defaultSettings = new RoomSettings(
            Arrays.asList("Państwa", "Miasta", "Zwierzęta", "Rośliny", "Imiona"),
            1, // 1 minute
            5, // 5 rounds
            8  // max 8 players
        );

        Room room = new Room(
            code,
            request.getIsPublic() != null ? request.getIsPublic() : false,
            Room.RoomStatus.lobby,
            new ArrayList<>(Arrays.asList(host)),
            new HashMap<>(),
            defaultSettings,
            new GameState(0, null, null, null, new HashMap<>(), new HashMap<>(), new ArrayList<>(), 0, 0)
        );

        host.setRoomCode(code);
        room = saveAndNotify(room);

        return new JoinResponse(code, playerId);
    }

    // List public rooms
    public List<PublicRoom> listPublicRooms() {
        List<Room> publicRooms = roomRepository.findPublicLobbies();
        List<PublicRoom> result = new ArrayList<>();

        for (Room room : publicRooms) {
            String hostNick = room.getPlayers().stream()
                .filter(Player::isHost)
                .map(Player::getNick)
                .findFirst().orElse("");
            result.add(new PublicRoom(
                room.getCode(),
                hostNick,
                room.getPlayers().size(),
                room.getSettings().getMaxPlayers()
            ));
        }
        return result;
    }

    // Get room by code (polling endpoint).
    // Atomically auto-stops an expired round, then computes the live mainTimeLeft.
    @Transactional
    public Room getRoom(String code) {
        long now = System.currentTimeMillis();
        roomRepository.autoStopIfExpired(code, now, Room.RoomStatus.playing, Room.RoomStatus.reviewing);

        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            return null;
        }

        int mainTimeLeft = 0;
        if (room.getStatus() == Room.RoomStatus.playing && room.getGame().getRoundEndsAt() != null) {
            mainTimeLeft = (int) Math.max(0, (room.getGame().getRoundEndsAt() - now) / 1000);
        }
        room.getGame().setMainTimeLeft(mainTimeLeft);
        room.getGame().setTimeLeft(0);
        room.getPlayers().size();
        return room;
    }

    // Join room
    @Transactional
    public JoinResponse joinRoom(String code, JoinRoomRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.lobby) {
            throw new RuntimeException("Game already started");
        }
        if (room.getPlayers().size() >= room.getSettings().getMaxPlayers()) {
            throw new RuntimeException("Room is full");
        }

        // Check if nick already exists
        Optional<Player> existingPlayer = room.getPlayers().stream()
            .filter(p -> p.getNick().equals(request.getNick()))
            .findFirst();

        if (existingPlayer.isPresent()) {
            return new JoinResponse(code, existingPlayer.get().getId());
        }

        UUID playerId = UUID.randomUUID();
        Player newPlayer = new Player(playerId, request.getNick(), false);
        newPlayer.setRoomCode(code);
        room.getPlayers().add(newPlayer);
        saveAndNotify(room);

        return new JoinResponse(code, playerId);
    }

    // Update settings
    @Transactional
    public void updateSettings(String code, UpdateSettingsRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.lobby) {
            throw new RuntimeException("Cannot change settings (game not in lobby)");
        }
        Player host = room.getPlayers().stream()
            .filter(Player::isHost)
            .findFirst().orElse(null);
        if (host == null || !host.getId().equals(request.getPlayerId())) {
            throw new RuntimeException("Only host can change settings");
        }

        RoomSettings newSettings = request.getSettings();
        if (newSettings.getCategories() != null) {
            room.getSettings().setCategories(newSettings.getCategories());
        }
        if (newSettings.getTimePerRound() != null) {
            room.getSettings().setTimePerRound(newSettings.getTimePerRound());
        }
        if (newSettings.getRounds() != null) {
            room.getSettings().setRounds(newSettings.getRounds());
        }
        if (newSettings.getMaxPlayers() != null) {
            room.getSettings().setMaxPlayers(newSettings.getMaxPlayers());
        }
        saveAndNotify(room);
    }

    // Start game
    @Transactional
    public void startGame(String code, PlayerIdRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.lobby && room.getStatus() != Room.RoomStatus.reviewing) {
            throw new RuntimeException("Cannot start game (wrong status)");
        }
        Player host = room.getPlayers().stream()
            .filter(Player::isHost)
            .findFirst().orElse(null);
        if (host == null || !host.getId().equals(request.getPlayerId())) {
            throw new RuntimeException("Only host can start game");
        }

        // Reset scores if first round
        if (room.getGame().getCurrentRound() == 0) {
            room.getScores().clear();
            for (Player p : room.getPlayers()) {
                room.getScores().put(p.getId().toString(), 0);
            }
        }

        // Draw random letter
        String letter = String.valueOf((char) ('A' + random.nextInt(26)));

        long startedAt = System.currentTimeMillis();
        room.getGame().setCurrentRound(room.getGame().getCurrentRound() + 1);
        room.getGame().setCurrentLetter(letter);
        room.getGame().setRoundStartedAt(startedAt);
        room.getGame().setRoundEndsAt(startedAt + room.getSettings().getTimePerRound() * 60_000L);
        room.getGame().getAnswers().clear();
        room.getGame().getVotes().clear();
        room.getGame().getStoppedPlayers().clear();

        room.setStatus(Room.RoomStatus.playing);
        saveAndNotify(room);
    }

    // Trigger stop
    @Transactional
    public void triggerStop(String code, PlayerIdRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.playing) {
            throw new RuntimeException("Not in playing phase");
        }
        Player player = room.getPlayers().stream()
            .filter(p -> p.getId().equals(request.getPlayerId()))
            .findFirst().orElse(null);
        if (player == null) {
            throw new RuntimeException("Player not in room");
        }

        String playerIdStr = request.getPlayerId().toString();
        if (!room.getGame().getStoppedPlayers().contains(playerIdStr)) {
            room.getGame().getStoppedPlayers().add(playerIdStr);
        }

        // If all players stopped, transition to reviewing
        if (room.getGame().getStoppedPlayers().size() == room.getPlayers().size()) {
            room.setStatus(Room.RoomStatus.reviewing);
        }
        saveAndNotify(room);
    }

    // Submit answers
    @Transactional
    public void submitAnswers(String code, SubmitAnswersRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        // Can submit even if stopped, I think
        Player player = room.getPlayers().stream()
            .filter(p -> p.getId().equals(request.getPlayerId()))
            .findFirst().orElse(null);
        if (player == null) {
            throw new RuntimeException("Player not in room");
        }

        room.getGame().getAnswers().put(request.getPlayerId().toString(), request.getAnswers());
        saveAndNotify(room);
    }

    // Submit vote
    @Transactional
    public void submitVote(String code, SubmitVoteRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.reviewing) {
            throw new RuntimeException("Not in reviewing phase");
        }
        Player voter = room.getPlayers().stream()
            .filter(p -> p.getId().equals(request.getVoterId()))
            .findFirst().orElse(null);
        if (voter == null) {
            throw new RuntimeException("Voter not in room");
        }

        String targetId = request.getTargetPlayerId().toString();
        String voterId = request.getVoterId().toString();
        String category = request.getCategory();

        room.getGame().getVotes()
            .computeIfAbsent(targetId, k -> new HashMap<>())
            .computeIfAbsent(category, k -> new HashMap<>())
            .put(voterId, request.isValid());
        saveAndNotify(room);
    }

    // Next round
    @Transactional
    public void nextRound(String code, PlayerIdRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        if (room.getStatus() != Room.RoomStatus.reviewing) {
            throw new RuntimeException("Not in reviewing phase");
        }
        Player host = room.getPlayers().stream()
            .filter(Player::isHost)
            .findFirst().orElse(null);
        if (host == null || !host.getId().equals(request.getPlayerId())) {
            throw new RuntimeException("Only host can end the round");
        }

        // Calculate scores
        calculateScores(room);

        if (room.getGame().getCurrentRound() >= room.getSettings().getRounds()) {
            room.setStatus(Room.RoomStatus.finished);
            room.getGame().setRoundEndsAt(null);
            saveAndNotify(room);
        } else {
            // Start next round
            saveAndNotify(room);
            startGame(code, request);
        }
    }

    private void calculateScores(Room room) {
        Map<String, Map<String, String>> answers = room.getGame().getAnswers();
        Map<String, Map<String, Map<String, Boolean>>> votes = room.getGame().getVotes();
        List<String> categories = room.getSettings().getCategories();

        // For each player's answers
        for (Map.Entry<String, Map<String, String>> playerEntry : answers.entrySet()) {
            String playerId = playerEntry.getKey();
            Map<String, String> playerAnswers = playerEntry.getValue();

            // For each category
            for (String category : categories) {
                String answer = playerAnswers.get(category);
                if (answer == null || answer.trim().isEmpty()) {
                    continue;
                }

                // Check votes for this answer
                Map<String, Boolean> categoryVotes = votes.getOrDefault(playerId, new HashMap<>()).get(category);

                // Count votes
                long validVotes = 0;
                long invalidVotes = 0;
                if (categoryVotes != null) {
                    validVotes = categoryVotes.values().stream().filter(v -> v).count();
                    invalidVotes = categoryVotes.values().stream().filter(v -> !v).count();
                }

                // Is answer valid? (no negative votes beat positive votes)
                boolean isValid = validVotes >= invalidVotes;

                if (!isValid) {
                    continue; // Skip invalid answers
                }

                // Check uniqueness - count other valid players with same answer
                int sameAnswerCount = 0;
                boolean someoneElseAnswered = false;

                for (Map.Entry<String, Map<String, String>> otherEntry : answers.entrySet()) {
                    String otherId = otherEntry.getKey();
                    if (otherId.equals(playerId)) continue;

                    String otherAnswer = otherEntry.getValue().get(category);
                    if (otherAnswer == null || otherAnswer.trim().isEmpty()) continue;

                    someoneElseAnswered = true;

                    // Check if other answer is valid
                    Map<String, Boolean> otherVotes = votes.getOrDefault(otherId, new HashMap<>()).get(category);
                    long otherValidVotes = 0;
                    long otherInvalidVotes = 0;
                    if (otherVotes != null) {
                        otherValidVotes = otherVotes.values().stream().filter(v -> v).count();
                        otherInvalidVotes = otherVotes.values().stream().filter(v -> !v).count();
                    }

                    if (otherValidVotes >= otherInvalidVotes) {
                        // Other answer is valid
                        if (otherAnswer.trim().toLowerCase().equals(answer.trim().toLowerCase())) {
                            sameAnswerCount++;
                        }
                    }
                }

                // Award points
                int points = 0;
                if (!someoneElseAnswered) {
                    points = 15; // Only one valid answer
                } else if (sameAnswerCount == 0) {
                    points = 10; // Unique answer
                } else {
                    points = 5; // Duplicate answer
                }

                room.getScores().put(playerId, room.getScores().get(playerId) + points);
            }
        }
    }

    // Reset to lobby
    @Transactional
    public void resetToLobby(String code, PlayerIdRequest request) {
        Room room = roomRepository.findById(code).orElse(null);
        if (room == null) {
            throw new RuntimeException("Room not found");
        }
        Player host = room.getPlayers().stream()
            .filter(Player::isHost)
            .findFirst().orElse(null);
        if (host == null || !host.getId().equals(request.getPlayerId())) {
            throw new RuntimeException("Only host can reset");
        }

        room.setStatus(Room.RoomStatus.lobby);
        room.getScores().clear();
        room.getGame().setCurrentRound(0);
        room.getGame().setCurrentLetter(null);
        room.getGame().setRoundStartedAt(null);
        room.getGame().setRoundEndsAt(null);
        room.getGame().getAnswers().clear();
        room.getGame().getVotes().clear();
        room.getGame().getStoppedPlayers().clear();
        saveAndNotify(room);
    }
}
