import { useState, useEffect, useRef, useCallback } from "react";
import { useNavigate, useLocation } from "react-router-dom";
import { api, ensureSession, getAccessToken } from "../services/api";
import type { Room } from "../types";

const PING_INTERVAL_MS = 30000;
const MAX_RECONNECT_DELAY_MS = 30000;

export const useWebSocketSync = (roomCode: string, onReviewingPhase?: () => void) => {
    const navigate = useNavigate();
    const location = useLocation();
    
    const [room, setRoom] = useState<Room | null>(null);
    const [playerId, setPlayerId] = useState<string | null>(null);
    const [accessToken, setAccessTokenState] = useState<string | null>(null);
    const [sessionReady, setSessionReady] = useState(false);
    
    const wsRef = useRef<WebSocket | null>(null);
    const reconnectDelayRef = useRef(1000);
    const reconnectTimerRef = useRef<number | null>(null);
    const pingTimerRef = useRef<number | null>(null);
    const mountedRef = useRef(true);
    
    const accessTokenRef = useRef<string | null>(null);
    const playerIdRef = useRef<string | null>(null);

    useEffect(() => {
        accessTokenRef.current = accessToken;
    }, [accessToken]);

    useEffect(() => {
        playerIdRef.current = playerId;
    }, [playerId]);

    useEffect(() => {
        if (!roomCode) {
            navigate("/");
            return;
        }

        let cancelled = false;
        (async () => {
            const session = await ensureSession(roomCode);
            if (cancelled) return;
            if (!session) {
                navigate("/");
                return;
            }
            setPlayerId(session.playerId);
            setAccessTokenState(session.accessToken);
            setSessionReady(true);
        })();

        return () => {
            cancelled = true;
        };
    }, [roomCode, navigate]);

    const processRoomUpdate = useCallback(async (data: Room) => {
        setRoom(data);

        if (data.status === "playing" && location.pathname.includes("/room")) {
            navigate(`/game?code=${roomCode}`);
        }
        if (data.status === "lobby" && location.pathname.includes("/game")) {
            navigate(`/room?code=${roomCode}`);
        }

        if (data.status === "reviewing" && playerIdRef.current) {
            if (onReviewingPhase) {
                onReviewingPhase();
            }
        }
    }, [location.pathname, navigate, roomCode, onReviewingPhase]);

    const tryRejoin = useCallback(async () => {
        const session = await ensureSession(roomCode);
        if (session) {
            setPlayerId(session.playerId);
            setAccessTokenState(session.accessToken);
            return session;
        }
        navigate("/");
        return null;
    }, [navigate, roomCode]);

    const fetchFallback = useCallback(async () => {
        try {
            const data = await api.getRoomState(roomCode);
            await processRoomUpdate(data);
        } catch {
            const session = await tryRejoin();
            if (session) {
                try {
                    const data = await api.getRoomState(roomCode);
                    await processRoomUpdate(data);
                } catch {
                    navigate("/");
                }
            }
        }
    }, [navigate, processRoomUpdate, roomCode, tryRejoin]);

    useEffect(() => {
        if (!roomCode || !sessionReady || !accessToken || !playerId) {
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

            const token = accessTokenRef.current || getAccessToken(roomCode);
            if (!token) {
                tryRejoin().then((session) => {
                    if (session) connect();
                });
                return;
            }

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
                ws.send(JSON.stringify({ type: "subscribe", token }));
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
                        tryRejoin();
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
    }, [roomCode, sessionReady, accessToken, playerId, fetchFallback, tryRejoin, processRoomUpdate]);

    return {
        room,
        setRoom,
        playerId,
        sessionReady,
        wsRef
    };
};
