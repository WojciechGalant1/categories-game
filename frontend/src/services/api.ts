import { ApiHttpError } from './errors';
import type { JoinResponse, PublicRoomSummary, Room, SuccessResponse } from '../types';

const API_URL = '/api';

const authHeaders = (token: string) => ({
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
});

type ApiErrorBody = { error?: string };

const parseJsonResponse = async <T>(res: Response): Promise<T> => {
    const body = (await res.json().catch(() => ({}))) as ApiErrorBody & T;
    if (!res.ok) {
        throw new ApiHttpError(res.status, body.error || res.statusText);
    }
    return body;
};

const authFetch = async (
    roomCode: string,
    url: string,
    options: RequestInit = {},
    retried = false,
): Promise<Response> => {
    const token = getAccessToken(roomCode);
    if (!token) {
        throw new ApiHttpError(401, 'No access token');
    }

    const res = await fetch(url, {
        ...options,
        headers: {
            ...authHeaders(token),
            ...(options.headers || {}),
        },
    });

    if (res.status === 401 && roomCode && !retried) {
        clearSession(roomCode);
        const session = await ensureSession(roomCode);
        if (session) {
            return authFetch(roomCode, url, options, true);
        }
    }

    if (!res.ok) {
        const body = (await res.json().catch(() => ({}))) as ApiErrorBody;
        throw new ApiHttpError(res.status, body.error || res.statusText);
    }

    return res;
};

const authJson = async <T>(
    roomCode: string,
    url: string,
    options: RequestInit = {},
): Promise<T> => {
    const res = await authFetch(roomCode, url, options);
    return res.json();
};

export const api = {
    getRooms: async (): Promise<PublicRoomSummary[]> => {
        const res = await fetch(`${API_URL}/rooms`);
        return parseJsonResponse<PublicRoomSummary[]>(res);
    },
    createRoom: async (nick: string, isPublic: boolean): Promise<JoinResponse> => {
        const res = await fetch(`${API_URL}/rooms`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick, isPublic }),
        });
        return parseJsonResponse<JoinResponse>(res);
    },
    joinRoom: async (code: string, nick: string): Promise<JoinResponse> => {
        const res = await fetch(`${API_URL}/rooms/${code}/join`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick }),
        });
        return parseJsonResponse<JoinResponse>(res);
    },
    getRoomState: async (code: string): Promise<Room> => {
        return authJson<Room>(code, `${API_URL}/rooms/${code}`);
    },
    updateSettings: async (code: string, settings: Record<string, unknown>): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/settings`, {
            method: 'POST',
            body: JSON.stringify({ settings }),
        });
    },
    startGame: async (code: string): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/start`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
    },
    triggerStop: async (code: string): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/stop`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
    },
    submitAnswers: async (code: string, answers: Record<string, string>): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/answers`, {
            method: 'POST',
            body: JSON.stringify({ answers }),
        });
    },
    nextRound: async (code: string): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/next-round`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
    },
    submitVote: async (code: string, targetPlayerId: string, category: string, isValid: boolean): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/vote`, {
            method: 'POST',
            body: JSON.stringify({ targetPlayerId, category, isValid }),
        });
    },
    resetToLobby: async (code: string): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/reset`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
    },
    leaveRoom: async (code: string): Promise<SuccessResponse> => {
        return authJson<SuccessResponse>(code, `${API_URL}/rooms/${code}/leave`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
    },
};

export const setAccessToken = (code: string, token: string) => {
    localStorage.setItem(`accessToken_${code}`, token);
};

export const getAccessToken = (code: string) => {
    return localStorage.getItem(`accessToken_${code}`);
};

export const clearAccessToken = (code: string) => {
    localStorage.removeItem(`accessToken_${code}`);
};

export const setPlayerId = (code: string, playerId: string) => {
    localStorage.setItem(`playerId_${code}`, playerId);
};

export const getPlayerId = (code: string) => {
    return localStorage.getItem(`playerId_${code}`);
};

export const clearPlayerId = (code: string) => {
    localStorage.removeItem(`playerId_${code}`);
};

export const setNick = (nick: string) => {
    localStorage.setItem('userNick', nick);
};

export const getNick = () => {
    return localStorage.getItem('userNick') || '';
};

export const saveSession = (code: string, playerId: string, accessToken: string) => {
    setPlayerId(code, playerId);
    setAccessToken(code, accessToken);
};

export const clearSession = (code: string) => {
    clearPlayerId(code);
    clearAccessToken(code);
};

/** Rejoin with nick + room code when token was lost (F5, new tab). */
export const ensureSession = async (roomCode: string): Promise<{ playerId: string; accessToken: string } | null> => {
    const token = getAccessToken(roomCode);
    const playerId = getPlayerId(roomCode);
    if (token && playerId) {
        return { playerId, accessToken: token };
    }

    const nick = getNick();
    if (!nick) {
        return null;
    }

    try {
        const res = await api.joinRoom(roomCode, nick);
        if (!res.accessToken) {
            return null;
        }
        saveSession(roomCode, res.playerId, res.accessToken);
        return { playerId: res.playerId, accessToken: res.accessToken };
    } catch {
        return null;
    }
};
