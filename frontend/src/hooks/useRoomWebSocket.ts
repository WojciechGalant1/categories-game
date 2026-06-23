import { useState, useEffect, useRef } from "react";
import { api } from "../services/api";
import type { Player } from "../types";
import { useWebSocketSync } from "./useWebSocketSync";
import { useGameTimer } from "./useGameTimer";
import { useGameActions } from "./useGameActions";

export const useRoomWebSocket = (roomCode: string) => {
    const [answers, setAnswers] = useState<Record<string, string>>({});
    const answersRef = useRef(answers);
    const submittedRef = useRef(false);

    useEffect(() => {
        answersRef.current = answers;
    }, [answers]);

    const handleReviewingPhase = () => {
        if (!submittedRef.current) {
            submittedRef.current = true;
            api.submitAnswers(roomCode, answersRef.current).catch(err => {
                console.error("Błąd wysyłania odpowiedzi", err);
            });
        }
    };

    const { room, setRoom, playerId, sessionReady, wsRef } = useWebSocketSync(
        roomCode,
        handleReviewingPhase
    );

    const { displayTimeLeft } = useGameTimer(room);

    const me = room?.players?.find((p: Player) => p.id === playerId);
    const isHost = me?.isHost ?? false;

    const gameActions = useGameActions(roomCode, room, isHost, setRoom, wsRef);

    useEffect(() => {
        if (room?.game?.currentRound) {
            setAnswers({});
            submittedRef.current = false;
        }
    }, [room?.game?.currentRound]);

    const handleAnswerChange = (category: string, value: string) => {
        setAnswers((prev) => ({ ...prev, [category]: value }));
    };

    const handleNextRound = async () => {
        await gameActions.handleNextRound();
        submittedRef.current = false;
        setAnswers({});
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
        // Spread the rest of actions
        toggleCategory: gameActions.toggleCategory,
        updateTime: gameActions.updateTime,
        updateRounds: gameActions.updateRounds,
        handleStartGame: gameActions.handleStartGame,
        handleStopClick: gameActions.handleStopClick,
        handleVote: gameActions.handleVote,
        handleReset: gameActions.handleReset,
        handleLeaveRoom: gameActions.handleLeaveRoom,
    };
};
