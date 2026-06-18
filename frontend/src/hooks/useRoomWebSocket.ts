import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { api, clearPlayerId } from "../services/api";

const PING_INTERVAL_MS = 30000;
const MAX_RECONNECT_DELAY_MS = 30000;

export const useRoomWebSocket = (roomCode: string, playerId: string | null) => {
    const navigate = useNavigate();
    const location = useLocation();
    const [room, setRoom] = useState<any>(null);
    const [answers, setAnswers] = useState<Record<string, string>>({});
    const [displayTimeLeft, setDisplayTimeLeft] = useState<number | undefined>();
    const answersRef = useRef(answers);
    const submittedRef = useRef(false);
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectDelayRef = useRef(1000);
    const reconnectTimerRef = useRef<number | null>(null);
    const pingTimerRef = useRef<number | null>(null);
    const mountedRef = useRef(true);

    useEffect(() => {
        answersRef.current = answers;
    }, [answers]);

    useEffect(() => {
        if (room?.game?.currentRound) {
            setAnswers({});
            submittedRef.current = false;
        }
    }, [room?.game?.currentRound]);

    const processRoomUpdate = useCallback(async (data: any) => {
        setRoom(data);

        if (data.status === "playing" && location.pathname.includes("/room")) {
            navigate(`/game?code=${roomCode}`);
        }
        if (data.status === "lobby" && location.pathname.includes("/game")) {
            navigate(`/room?code=${roomCode}`);
        }

        if (data.status === "reviewing" && !submittedRef.current) {
            submittedRef.current = true;
            try {
                await api.submitAnswers(roomCode, playerId!, answersRef.current);
            } catch (err) {
                console.error("Błąd wysyłania odpowiedzi", err);
            }
        }
    }, [location.pathname, navigate, playerId, roomCode]);

    const fetchFallback = useCallback(async () => {
        try {
            const data = await api.getRoomState(roomCode);
            await processRoomUpdate(data);
        } catch {
            navigate("/");
        }
    }, [navigate, processRoomUpdate, roomCode]);

    useEffect(() => {
        if (!roomCode || !playerId) {
            navigate("/");
            return;
        }

        mountedRef.current = true;
        reconnectDelayRef.current = 1000;

        const clearTimers = () => {
            if (pingTimerRef.current !== null) {
                clearInterval(pingTimerRef.current);
                pingTimerRef.current = null;
            }
            if (reconnectTimerRef.current !== null) {
                clearTimeout(reconnectTimerRef.current);
                reconnectTimerRef.current = null;
            }
        };

        const scheduleReconnect = () => {
            if (!mountedRef.current) return;
            const delay = reconnectDelayRef.current;
            reconnectDelayRef.current = Math.min(delay * 2, MAX_RECONNECT_DELAY_MS);
            reconnectTimerRef.current = window.setTimeout(() => {
                connect();
            }, delay);
        };

        const connect = () => {
            if (!mountedRef.current) return;

            clearTimers();
            if (wsRef.current) {
                wsRef.current.close();
                wsRef.current = null;
            }

            const protocol = window.location.protocol === "https:" ? "wss:" : "ws:";
            const ws = new WebSocket(`${protocol}//${window.location.host}/api/ws/rooms/${roomCode}`);
            wsRef.current = ws;

            ws.onopen = () => {
                reconnectDelayRef.current = 1000;
                ws.send(JSON.stringify({ type: "subscribe", playerId }));
                pingTimerRef.current = window.setInterval(() => {
                    if (ws.readyState === WebSocket.OPEN) {
                        ws.send(JSON.stringify({ type: "ping" }));
                    }
                }, PING_INTERVAL_MS);
            };

            ws.onmessage = (event) => {
                try {
                    const msg = JSON.parse(event.data);
                    if (msg.type === "room_state") {
                        processRoomUpdate(msg.data);
                    } else if (msg.type === "error") {
                        console.error("WebSocket error:", msg.message);
                        navigate("/");
                    }
                } catch (err) {
                    console.error("Invalid WebSocket message", err);
                }
            };

            ws.onerror = () => {
                fetchFallback();
            };

            ws.onclose = () => {
                clearTimers();
                if (mountedRef.current) {
                    fetchFallback();
                    scheduleReconnect();
                }
            };
        };

        connect();

        return () => {
            mountedRef.current = false;
            clearTimers();
            if (wsRef.current) {
                wsRef.current.close();
                wsRef.current = null;
            }
        };
    }, [roomCode, playerId, navigate, processRoomUpdate, fetchFallback]);

    useEffect(() => {
        if (room?.status === "playing" && room?.game?.mainTimeLeft !== undefined) {
            setDisplayTimeLeft(room.game.mainTimeLeft);
        } else if (room?.status !== "playing") {
            setDisplayTimeLeft(undefined);
        }
    }, [room?.game?.mainTimeLeft, room?.status, room?.game?.currentRound]);

    useEffect(() => {
        if (room?.status !== "playing") {
            return;
        }
        const id = window.setInterval(() => {
            setDisplayTimeLeft((t) => (t !== undefined && t > 0 ? t - 1 : t ?? 0));
        }, 1000);
        return () => clearInterval(id);
    }, [room?.status, room?.game?.currentRound]);

    const me = room?.players?.find((p: any) => p.id === playerId);
    const isHost = me?.isHost ?? false;

    const toggleCategory = async (category: string) => {
        if (!isHost || !room) return;
        const currentCats = room.settings.categories || [];
        const newCats = currentCats.includes(category)
            ? currentCats.filter((c: string) => c !== category)
            : [...currentCats, category];

        setRoom((prev: any) =>
            prev ? { ...prev, settings: { ...prev.settings, categories: newCats } } : prev
        );
        await api.updateSettings(roomCode, playerId!, { categories: newCats });
    };

    const updateTime = async (t: number) => {
        if (!isHost || !room) return;
        setRoom((prev: any) =>
            prev ? { ...prev, settings: { ...prev.settings, timePerRound: t } } : prev
        );
        await api.updateSettings(roomCode, playerId!, { timePerRound: t });
    };

    const updateRounds = async (change: number) => {
        if (!isHost || !room) return;
        const newRounds = Math.max(1, Math.min(20, room.settings.rounds + change));
        setRoom((prev: any) =>
            prev ? { ...prev, settings: { ...prev.settings, rounds: newRounds } } : prev
        );
        await api.updateSettings(roomCode, playerId!, { rounds: newRounds });
    };

    const handleStartGame = async () => {
        if (!isHost) return;
        try {
            await api.startGame(roomCode, playerId!);
        } catch {
            alert("Błąd startu gry");
        }
    };

    const handleAnswerChange = (category: string, value: string) => {
        setAnswers((prev) => ({ ...prev, [category]: value }));
    };

    const handleStopClick = async () => {
        try {
            await api.triggerStop(roomCode, playerId!);
        } catch {
            alert("Błąd, nie udało się zatrzymać gry.");
        }
    };

    const handleNextRound = async () => {
        try {
            await api.nextRound(roomCode, playerId!);
            submittedRef.current = false;
            setAnswers({});
        } catch {
            alert("Błąd przy przejściu do następnej rundy.");
        }
    };

    const handleVote = async (targetPlayerId: string, category: string, isValid: boolean) => {
        try {
            await api.submitVote(roomCode, playerId!, targetPlayerId, category, isValid);
        } catch (err) {
            console.error("Błąd głosowania", err);
        }
    };

    const handleReset = async () => {
        try {
            await api.resetToLobby(roomCode, playerId!);
        } catch (err) {
            console.error("Błąd resetowania", err);
        }
    };

    const handleLeaveRoom = async () => {
        mountedRef.current = false;
        try {
            await api.leaveRoom(roomCode, playerId!);
        } catch {
            // Room may already be gone — still leave locally
        }
        if (wsRef.current) {
            wsRef.current.close();
            wsRef.current = null;
        }
        clearPlayerId(roomCode);
        navigate("/");
    };

    return {
        room,
        isHost,
        playerId,
        answers,
        displayTimeLeft,
        toggleCategory,
        updateTime,
        updateRounds,
        handleStartGame,
        handleAnswerChange,
        handleStopClick,
        handleNextRound,
        handleVote,
        handleReset,
        handleLeaveRoom,
    };
};
