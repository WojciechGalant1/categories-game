import { useSearchParams } from "react-router-dom";
import { getPlayerId } from "../services/api";
import { useRoomWebSocket } from "../hooks/useRoomWebSocket";
import { PlayerList } from "../components/room/PlayerList";
import { RoomSettings } from "../components/room/RoomSettings";

const Room = () => {
    const [searchParams] = useSearchParams();
    const roomCode = searchParams.get("code") || "";
    const playerId = getPlayerId(roomCode);

    const {
        room,
        isHost,
        toggleCategory,
        updateTime,
        updateRounds,
        handleStartGame,
    } = useRoomWebSocket(roomCode, playerId);

    if (!room) return <div className="pt-32 text-center text-white">Ładowanie poczekalni...</div>;

    return (
        <div className="pt-32 pb-20 min-h-screen flex flex-col items-center px-4 relative">
            <div className="absolute top-1/4 left-10 w-64 h-64 bg-brand-primary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-100 hidden md:block"></div>
            <div className="absolute top-1/3 right-10 w-72 h-72 bg-brand-secondary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-500 hidden md:block"></div>
            
            <div className="text-center mb-8 animate-fade-in-up delay-100 relative z-10 max-w-2xl mx-auto">
                <p className="text-brand-accent font-semibold text-sm uppercase tracking-widest mb-2">Poczekalnia</p>
                <h1 className="text-4xl md:text-6xl font-bold mb-2 text-white">
                    Kod pokoju: <span className="text-transparent bg-clip-text bg-gradient-to-r from-brand-accent to-emerald-300 tracking-widest font-display ml-2">{roomCode}</span>
                </h1>
                <p className="text-gray-400">Wyślij ten kod znajomym, aby mogli dołączyć do gry</p>
            </div>

            <div className="w-full max-w-5xl grid lg:grid-cols-12 gap-8 relative z-10">
                {/* LEFT COLUMN - PLAYERS */}
                <div className="lg:col-span-4 flex flex-col gap-4 animate-fade-in-up delay-200">
                    <PlayerList players={room.players} maxPlayers={room.settings.maxPlayers} playerId={playerId!} />
                </div>

                {/* RIGHT COLUMN - SETTINGS */}
                <div className="lg:col-span-8 flex flex-col gap-4 animate-fade-in-up delay-300">
                    <RoomSettings 
                        settings={room.settings}
                        isHost={isHost}
                        toggleCategory={toggleCategory}
                        updateTime={updateTime}
                        updateRounds={updateRounds}
                        handleStartGame={handleStartGame}
                    />
                </div>
            </div>
            
        </div>
    );
};

export default Room;