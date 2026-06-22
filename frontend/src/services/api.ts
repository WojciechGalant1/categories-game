const API_URL = '/api';

const authHeaders = (token: string) => ({
    'Content-Type': 'application/json',
    Authorization: `Bearer ${token}`,
});

const handleAuthError = (res: Response, roomCode?: string) => {
    if (res.status === 401 && roomCode) {
        clearAccessToken(roomCode);
        clearPlayerId(roomCode);
    }
};

const authFetch = async (roomCode: string, url: string, options: RequestInit = {}) => {
    const token = getAccessToken(roomCode);
    if (!token) {
        throw new Error('No access token');
    }
    const res = await fetch(url, {
        ...options,
        headers: {
            ...authHeaders(token),
            ...(options.headers || {}),
        },
    });
    handleAuthError(res, roomCode);
    return res;
};

export const api = {
    getRooms: async () => {
        const res = await fetch(`${API_URL}/rooms`);
        return res.json();
    },
    createRoom: async (nick: string, isPublic: boolean) => {
        const res = await fetch(`${API_URL}/rooms`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick, isPublic }),
        });
        return res.json();
    },
    joinRoom: async (code: string, nick: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/join`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick }),
        });
        return res.json();
    },
    getRoomState: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}`);
        if (!res.ok) throw new Error('Room not found');
        return res.json();
    },
    updateSettings: async (code: string, settings: Record<string, unknown>) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/settings`, {
            method: 'POST',
            body: JSON.stringify({ settings }),
        });
        return res.json();
    },
    startGame: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/start`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        return res.json();
    },
    triggerStop: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/stop`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        return res.json();
    },
    submitAnswers: async (code: string, answers: Record<string, string>) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/answers`, {
            method: 'POST',
            body: JSON.stringify({ answers }),
        });
        return res.json();
    },
    nextRound: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/next-round`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        return res.json();
    },
    submitVote: async (code: string, targetPlayerId: string, category: string, isValid: boolean) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/vote`, {
            method: 'POST',
            body: JSON.stringify({ targetPlayerId, category, isValid }),
        });
        return res.json();
    },
    resetToLobby: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/reset`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        return res.json();
    },
    leaveRoom: async (code: string) => {
        const res = await authFetch(code, `${API_URL}/rooms/${code}/leave`, {
            method: 'POST',
            body: JSON.stringify({}),
        });
        if (!res.ok) throw new Error('Leave failed');
        return res.json();
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

    const res = await api.joinRoom(roomCode, nick);
    if (res.error || !res.accessToken) {
        return null;
    }

    saveSession(roomCode, res.playerId, res.accessToken);
    return { playerId: res.playerId, accessToken: res.accessToken };
};
