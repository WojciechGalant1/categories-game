package com.example.panstwamiasta.service;

import com.example.panstwamiasta.auth.PlayerSessionTokenService;
import com.example.panstwamiasta.dto.*;
import com.example.panstwamiasta.exception.GameAlreadyStartedException;
import com.example.panstwamiasta.exception.InvalidRoomActionException;
import com.example.panstwamiasta.exception.RoomNotFoundException;
import com.example.panstwamiasta.model.GameState;
import com.example.panstwamiasta.model.Player;
import com.example.panstwamiasta.model.RoomSettings;
import com.example.panstwamiasta.room.Room;
import com.example.panstwamiasta.repository.RoomRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
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

    @Autowired
    private RoomTtlService roomTtlService;

    @Autowired
    private PlayerSessionTokenService tokenService;

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

    private Room requireRoom(String code) {
        return roomRepository.findById(code)
                .orElseThrow(RoomNotFoundException::new);
    }

    private Player requirePlayer(Room room, UUID playerId) {
        return room.getPlayers().stream()
                .filter(p -> p.getId().equals(playerId))
                .findFirst()
                .orElseThrow(() -> new InvalidRoomActionException("Player not in room"));
    }

    private void requireStatus(Room room, String message, Room.RoomStatus... allowed) {
        for (Room.RoomStatus status : allowed) {
            if (room.getStatus() == status) {
                return;
            }
        }
        throw new InvalidRoomActionException(message);
    }

    private String generateRoomCode() {
        String chars = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
        StringBuilder code = new StringBuilder();
        for (int i = 0; i < 6; i++) {
            code.append(chars.charAt(random.nextInt(chars.length())));
        }
        return code.toString();
    }

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
            1,
            5,
            8
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

        return toJoinResponse(code, host);
    }

    private JoinResponse toJoinResponse(String code, Player player) {
        String token = tokenService.issueToken(player.getId(), code, player.getNick(), player.isHost());
        return new JoinResponse(code, player.getId(), token);
    }

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

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public JoinResponse joinRoom(String code, JoinRoomRequest request) {
        Room room = requireRoom(code);

        Optional<Player> existingPlayer = room.getPlayers().stream()
            .filter(p -> p.getNick().equals(request.getNick()))
            .findFirst();

        if (existingPlayer.isPresent()) {
            return toJoinResponse(code, existingPlayer.get());
        }

        if (room.getStatus() != Room.RoomStatus.lobby) {
            throw new GameAlreadyStartedException();
        }
        if (room.getPlayers().size() >= room.getSettings().getMaxPlayers()) {
            throw new InvalidRoomActionException("Room is full");
        }

        UUID playerId = UUID.randomUUID();
        Player newPlayer = new Player(playerId, request.getNick(), false);
        newPlayer.setRoomCode(code);
        room.getPlayers().add(newPlayer);
        saveAndNotify(room);

        return toJoinResponse(code, newPlayer);
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void updateSettings(String code, UUID playerId, UpdateSettingsRequest request) {
        Room room = requireRoom(code);
        requireStatus(room, "Cannot change settings (game not in lobby)", Room.RoomStatus.lobby);

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

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void startGame(String code, UUID playerId) {
        Room room = requireRoom(code);
        requireStatus(room, "Cannot start game (wrong status)", Room.RoomStatus.lobby, Room.RoomStatus.reviewing);

        if (room.getGame().getCurrentRound() == 0) {
            room.getScores().clear();
            for (Player p : room.getPlayers()) {
                room.getScores().put(p.getId().toString(), 0);
            }
        }

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

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void triggerStop(String code, UUID playerId) {
        Room room = requireRoom(code);
        requireStatus(room, "Not in playing phase", Room.RoomStatus.playing);
        requirePlayer(room, playerId);

        String playerIdStr = playerId.toString();
        if (!room.getGame().getStoppedPlayers().contains(playerIdStr)) {
            room.getGame().getStoppedPlayers().add(playerIdStr);
        }

        if (room.getGame().getStoppedPlayers().size() == room.getPlayers().size()) {
            room.setStatus(Room.RoomStatus.reviewing);
        }
        saveAndNotify(room);
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void submitAnswers(String code, UUID playerId, SubmitAnswersRequest request) {
        Room room = requireRoom(code);
        requireStatus(room, "Cannot submit answers in current phase",
                Room.RoomStatus.playing, Room.RoomStatus.reviewing);
        requirePlayer(room, playerId);

        Map<String, String> newAnswers = request.getAnswers() != null ? request.getAnswers() : Map.of();
        String playerIdStr = playerId.toString();
        Map<String, String> existing = room.getGame().getAnswers().get(playerIdStr);
        boolean hasExisting = existing != null && !existing.isEmpty();

        if (newAnswers.isEmpty()) {
            return;
        }

        if (room.getStatus() == Room.RoomStatus.reviewing && hasExisting) {
            throw new InvalidRoomActionException("Answers are locked during review");
        }

        room.getGame().getAnswers().put(playerIdStr, newAnswers);
        saveAndNotify(room);
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void submitVote(String code, UUID voterId, SubmitVoteRequest request) {
        Room room = requireRoom(code);
        requireStatus(room, "Not in reviewing phase", Room.RoomStatus.reviewing);
        requirePlayer(room, voterId);

        String targetId = request.getTargetPlayerId().toString();
        String voterIdStr = voterId.toString();
        String category = request.getCategory();

        room.getGame().getVotes()
            .computeIfAbsent(targetId, k -> new HashMap<>())
            .computeIfAbsent(category, k -> new HashMap<>())
            .put(voterIdStr, request.isValid());
        saveAndNotify(room);
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void nextRound(String code, UUID playerId) {
        Room room = requireRoom(code);
        requireStatus(room, "Not in reviewing phase", Room.RoomStatus.reviewing);

        ScoreCalculator.apply(room);

        if (room.getGame().getCurrentRound() >= room.getSettings().getRounds()) {
            room.setStatus(Room.RoomStatus.finished);
            room.getGame().setRoundEndsAt(null);
            saveAndNotify(room);
        } else {
            saveAndNotify(room);
            startGame(code, playerId);
        }
    }

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void resetToLobby(String code, UUID playerId) {
        Room room = requireRoom(code);

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

    @Transactional
    @Retryable(retryFor = OptimisticLockingFailureException.class, maxAttempts = 3, backoff = @Backoff(delay = 50))
    public void leaveRoom(String code, UUID playerId) {
        Room room = requireRoom(code);
        Player leaving = requirePlayer(room, playerId);

        boolean wasHost = leaving.isHost();
        room.getPlayers().remove(leaving);

        String playerIdStr = playerId.toString();
        room.getScores().remove(playerIdStr);
        room.getGame().getAnswers().remove(playerIdStr);
        room.getGame().getVotes().remove(playerIdStr);
        room.getGame().getStoppedPlayers().remove(playerIdStr);

        if (room.getPlayers().isEmpty()) {
            roomRepository.deleteById(code);
            roomTtlService.cleanup(code);
            return;
        }

        if (wasHost) {
            room.getPlayers().get(0).setIsHost(true);
        }

        saveAndNotify(room);
    }
}
