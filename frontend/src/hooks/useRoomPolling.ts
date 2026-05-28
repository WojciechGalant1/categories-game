import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { api } from "../services/api";

export const useRoomPolling = (roomCode: string, playerId: string | null) => {
    const navigate = useNavigate();
    const [room, setRoom] = useState<any>(null);

    useEffect(() => {
        if (!roomCode || !playerId) {
            navigate('/');
            return;
        }

        const fetchRoom = async () => {
            try {
                const data = await api.getRoomState(roomCode);
                setRoom(data);
                
                if (data.status === 'playing') {
                    navigate(`/game?code=${roomCode}`);
                }
            } catch (err) {
                navigate('/');
            }
        };

        fetchRoom();
        const intId = setInterval(fetchRoom, 1500);
        return () => clearInterval(intId);
    }, [roomCode, playerId, navigate]);

    const me = room?.players?.find((p: any) => p.id === playerId);
    const isHost = me?.isHost ?? false;

    const toggleCategory = async (category: string) => {
        if (!isHost || !room) return;
        const currentCats = room.settings.categories || [];
        const newCats = currentCats.includes(category) 
            ? currentCats.filter((c: string) => c !== category)
            : [...currentCats, category];
        
        setRoom({ ...room, settings: { ...room.settings, categories: newCats }});
        await api.updateSettings(roomCode, playerId!, { categories: newCats });
    };

    const updateTime = async (t: number) => {
        if (!isHost || !room) return;
        setRoom({ ...room, settings: { ...room.settings, timePerRound: t }});
        await api.updateSettings(roomCode, playerId!, { timePerRound: t });
    };

    const updateRounds = async (change: number) => {
        if (!isHost || !room) return;
        const newRounds = Math.max(1, Math.min(20, room.settings.rounds + change));
        setRoom({ ...room, settings: { ...room.settings, rounds: newRounds }});
        await api.updateSettings(roomCode, playerId!, { rounds: newRounds });
    };

    const handleStartGame = async () => {
        if (!isHost) return;
        try {
            await api.startGame(roomCode, playerId!);
        } catch(err: any) {
            alert("Błąd startu gry");
        }
    };

    return {
        room,
        isHost,
        playerId,
        toggleCategory,
        updateTime,
        updateRounds,
        handleStartGame,
    };
};
