import { useState, useEffect, useRef } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";

export const useGamePolling = (roomCode: string, playerId: string | null) => {
    const navigate = useNavigate();
    const [room, setRoom] = useState<any>(null);
    const [answers, setAnswers] = useState<Record<string, string>>({});
    const answersRef = useRef(answers);
    const [submitted, setSubmitted] = useState(false);

    useEffect(() => {
        answersRef.current = answers;
    }, [answers]);

    // Reset local state when a new round starts
    useEffect(() => {
        if (room?.game?.currentRound) {
            setAnswers({});
            setSubmitted(false);
        }
    }, [room?.game?.currentRound]);

    useEffect(() => {
        if (!roomCode || !playerId) {
            navigate('/');
            return;
        }

        const fetchRoom = async () => {
            try {
                const data = await api.getRoomState(roomCode);
                setRoom(data);
                
                // Trigger submission if server transitioned to reviewing
                if (data.status === 'reviewing') {
                    if (!submitted) {
                        setSubmitted(true);
                        try {
                            await api.submitAnswers(roomCode, playerId!, answersRef.current);
                        } catch(err) {
                            console.error("Błąd wysyłania odpowiedzi", err);
                        }
                    }
                }
                
                if (data.status === 'lobby') {
                    navigate(`/room?code=${roomCode}`);
                }

            } catch (err) {
                console.error("Game error", err);
                navigate('/');
            }
        };

        fetchRoom();
        const intId = setInterval(fetchRoom, 1000);
        return () => clearInterval(intId);
    }, [roomCode, playerId, navigate, submitted]);

    const handleAnswerChange = (category: string, value: string) => {
        setAnswers(prev => ({ ...prev, [category]: value }));
    };

    const handleStopClick = async () => {
        try {
            await api.triggerStop(roomCode, playerId!);
        } catch(err) {
            alert("Błąd, nie udało się zatrzymać gry.");
        }
    };

    const handleNextRound = async () => {
        try {
            await api.nextRound(roomCode, playerId!);
            setSubmitted(false);
            setAnswers({});
        } catch(err) {
            alert("Błąd przy przejściu do następnej rundy.");
        }
    };

    const handleVote = async (targetPlayerId: string, category: string, isValid: boolean) => {
        try {
            await api.submitVote(roomCode, playerId!, targetPlayerId, category, isValid);
        } catch(err) {
            console.error("Błąd głosowania", err);
        }
    };

    const handleReset = async () => {
        try {
            await api.resetToLobby(roomCode, playerId!);
        } catch(err) {
            console.error("Błąd resetowania", err);
        }
    };

    return {
        room,
        answers,
        handleAnswerChange,
        handleStopClick,
        handleNextRound,
        handleVote,
        handleReset,
    };
};
