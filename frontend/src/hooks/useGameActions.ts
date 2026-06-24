import { useNavigate } from "react-router-dom";
import { api, clearSession } from "../services/api";
import { ApiHttpError } from "../services/errors";
import type { Room } from "../types";

const handleApiError = (err: unknown, navigate: ReturnType<typeof useNavigate>) => {
    if (err instanceof ApiHttpError) {
        if (err.isUnauthorized) {
            navigate("/");
            return;
        }
        if (err.isForbidden) {
            alert(err.message);
            return;
        }
        alert(err.message);
    }
};

export const useGameActions = (
    roomCode: string,
    room: Room | null,
    isHost: boolean,
    setRoom: React.Dispatch<React.SetStateAction<Room | null>>,
    wsRef: React.MutableRefObject<WebSocket | null>
) => {
    const navigate = useNavigate();

    const toggleCategory = async (category: string) => {
        if (!isHost || !room) return;
        const currentCats = room.settings.categories || [];
        const newCats = currentCats.includes(category)
            ? currentCats.filter((c: string) => c !== category)
            : [...currentCats, category];

        setRoom((prev: Room | null) =>
            prev ? { ...prev, settings: { ...prev.settings, categories: newCats } } : prev
        );
        try {
            await api.updateSettings(roomCode, { categories: newCats });
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const updateTime = async (t: number) => {
        if (!isHost || !room) return;
        setRoom((prev: Room | null) =>
            prev ? { ...prev, settings: { ...prev.settings, timePerRound: t } } : prev
        );
        try {
            await api.updateSettings(roomCode, { timePerRound: t });
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const updateRounds = async (change: number) => {
        if (!isHost || !room) return;
        const newRounds = Math.max(1, Math.min(20, room.settings.rounds + change));
        setRoom((prev: Room | null) =>
            prev ? { ...prev, settings: { ...prev.settings, rounds: newRounds } } : prev
        );
        try {
            await api.updateSettings(roomCode, { rounds: newRounds });
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleStartGame = async () => {
        if (!isHost) return;
        try {
            await api.startGame(roomCode);
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleStopClick = async () => {
        try {
            await api.triggerStop(roomCode);
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleNextRound = async () => {
        try {
            await api.nextRound(roomCode);
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleVote = async (targetPlayerId: string, category: string, isValid: boolean) => {
        try {
            await api.submitVote(roomCode, targetPlayerId, category, isValid);
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleReset = async () => {
        try {
            await api.resetToLobby(roomCode);
        } catch (err) {
            handleApiError(err, navigate);
        }
    };

    const handleLeaveRoom = async () => {
        try {
            await api.leaveRoom(roomCode);
        } catch {
            // Room may already be gone — still leave locally
        }
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }
        clearSession(roomCode);
        navigate("/");
    };

    return {
        toggleCategory,
        updateTime,
        updateRounds,
        handleStartGame,
        handleStopClick,
        handleNextRound,
        handleVote,
        handleReset,
        handleLeaveRoom,
    };
};
