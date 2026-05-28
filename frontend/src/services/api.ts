const API_URL = '/api';

export const api = {
    getRooms: async () => {
        const res = await fetch(`${API_URL}/rooms`);
        return res.json();
    },
    createRoom: async (nick: string, isPublic: boolean) => {
        const res = await fetch(`${API_URL}/rooms`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick, isPublic })
        });
        return res.json();
    },
    joinRoom: async (code: string, nick: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/join`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ nick })
        });
        return res.json();
    },
    getRoomState: async (code: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}`);
        if (!res.ok) throw new Error('Room not found');
        return res.json();
    },
    updateSettings: async (code: string, playerId: string, settings: any) => {
        const res = await fetch(`${API_URL}/rooms/${code}/settings`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId, settings })
        });
        return res.json();
    },
    startGame: async (code: string, playerId: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/start`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId })
        });
        return res.json();
    },
    triggerStop: async (code: string, playerId: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/stop`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId })
        });
        return res.json();
    },
    submitAnswers: async (code: string, playerId: string, answers: any) => {
        const res = await fetch(`${API_URL}/rooms/${code}/answers`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId, answers })
        });
        return res.json();
    },
    nextRound: async (code: string, playerId: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/next-round`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId })
        });
        return res.json();
    },
    submitVote: async (code: string, voterId: string, targetPlayerId: string, category: string, isValid: boolean) => {
        const res = await fetch(`${API_URL}/rooms/${code}/vote`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ voterId, targetPlayerId, category, isValid })
        });
        return res.json();
    },
    resetToLobby: async (code: string, playerId: string) => {
        const res = await fetch(`${API_URL}/rooms/${code}/reset`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify({ playerId })
        });
        return res.json();
    }
};

// Local storage helpers
export const setPlayerId = (code: string, playerId: string) => {
    localStorage.setItem(`playerId_${code}`, playerId);
};
export const getPlayerId = (code: string) => {
    return localStorage.getItem(`playerId_${code}`);
};
export const setNick = (nick: string) => {
    localStorage.setItem(`userNick`, nick);
};
export const getNick = () => {
    return localStorage.getItem(`userNick`) || '';
};
