import { useState, useEffect, useRef, useCallback } from "react";
import { api } from "../services/api";
import type { Player } from "../types";
import { useWebSocketSync } from "./useWebSocketSync";
import { useGameTimer } from "./useGameTimer";
import { useGameActions } from "./useGameActions";

const hasNonEmptyAnswers = (answers: Record<string, string>) =>
    Object.values(answers).some((v) => v.trim().length > 0);

export const useRoomWebSocket = (roomCode: string) => {
    const [answers, setAnswers] = useState<Record<string, string>>({});
    const answersRef = useRef(answers);
    const submittedRef = useRef(false);
    const lastPlayingRoundRef = useRef<number | null>(null);

    useEffect(() => {
        answersRef.current = answers;
    }, [answers]);

    const submitAnswersIfNeeded = useCallback(async () => {
        if (submittedRef.current) return;
        const payload = answersRef.current;
        if (!hasNonEmptyAnswers(payload)) return;

        submittedRef.current = true;
        try {
            await api.submitAnswers(roomCode, payload);
        } catch (err) {
            submittedRef.current = false;
            throw err;
        }
    }, [roomCode]);

    const handleReviewingPhase = useCallback(() => {
        submitAnswersIfNeeded().catch((err) => {
            console.error("Błąd wysyłania odpowiedzi", err);
        });
    }, [submitAnswersIfNeeded]);

    const { room, setRoom, playerId, sessionReady, wsRef } = useWebSocketSync(
        roomCode,
        handleReviewingPhase
    );

    const { displayTimeLeft } = useGameTimer(room);

    const me = room?.players?.find((p: Player) => p.id === playerId);
    const isHost = me?.isHost ?? false;

    const gameActions = useGameActions(roomCode, room, isHost, setRoom, wsRef);

    // Reset formularza tylko przy wejściu w nową rundę playing (nie przy playing → reviewing).
    useEffect(() => {
        if (room?.status !== "playing" || !room.game?.currentRound) return;

        const round = room.game.currentRound;
        if (lastPlayingRoundRef.current === round) return;

        lastPlayingRoundRef.current = round;
        setAnswers({});
        submittedRef.current = false;
    }, [room?.status, room?.game?.currentRound]);

    const handleAnswerChange = (category: string, value: string) => {
        setAnswers((prev) => ({ ...prev, [category]: value }));
    };

    const handleStopClick = async () => {
        try {
            await submitAnswersIfNeeded();
            await gameActions.handleStopClick();
        } catch (err) {
            console.error("Błąd wysyłania odpowiedzi", err);
        }
    };

    const handleNextRound = async () => {
        await gameActions.handleNextRound();
    };

    return {
        room,
        isHost,
        playerId,
        sessionReady,
        answers,
        displayTimeLeft,
        handleAnswerChange,
        handleNextRound,
        toggleCategory: gameActions.toggleCategory,
        updateTime: gameActions.updateTime,
        updateRounds: gameActions.updateRounds,
        handleStartGame: gameActions.handleStartGame,
        handleStopClick,
        handleVote: gameActions.handleVote,
        handleReset: gameActions.handleReset,
        handleLeaveRoom: gameActions.handleLeaveRoom,
    };
};
