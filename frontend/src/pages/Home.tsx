import { useState, useEffect } from "react";
import { useNavigate } from "react-router-dom";
import { api, setNick, getNick, saveSession } from "../services/api";
import type { PublicRoomSummary } from "../types";

const Home = () => {
    const navigate = useNavigate();
    const [nick, setNickState] = useState(getNick());
    const [roomCode, setRoomCode] = useState("");
    const [isPublic, setIsPublic] = useState(false);
    const [publicRooms, setPublicRooms] = useState<PublicRoomSummary[]>([]);

    useEffect(() => {
        const fetchRooms = async () => {
            try {
                const rooms = await api.getRooms();
                setPublicRooms(rooms);
            } catch (err) {
                console.error("Failed to fetch rooms", err);
            }
        };
        fetchRooms();
        const intId = setInterval(fetchRooms, 3000);
        return () => clearInterval(intId);
    }, []);

    const handleCreateRoom = async () => {
        if (!nick) return alert("Podaj nick!");
        setNick(nick);
        try {
            const res = await api.createRoom(nick, isPublic);
            if (res.error) throw new Error(res.error);
            saveSession(res.code, res.playerId, res.accessToken);
            navigate(`/room?code=${res.code}`);
        } catch (err: any) {
            alert(err.message);
        }
    };

    const handleJoinRoom = async (codeToJoin = roomCode) => {
        if (!nick) return alert("Podaj nick!");
        if (!codeToJoin) return alert("Podaj kod pokoju!");
        setNick(nick);
        try {
            const res = await api.joinRoom(codeToJoin, nick);
            if (res.error) throw new Error(res.error);
            saveSession(res.code, res.playerId, res.accessToken);
            navigate(`/room?code=${res.code}`);
        } catch (err: any) {
            alert(err.message);
        }
    };

    return (
        <div className="pt-32 pb-20 min-h-screen flex flex-col items-center px-4 relative">
            <div className="absolute top-1/4 left-10 w-64 h-64 bg-brand-primary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-100 hidden md:block z-0 pointer-events-none"></div>
            <div className="absolute top-1/3 right-10 w-72 h-72 bg-brand-secondary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-500 hidden md:block z-0 pointer-events-none"></div>
            
            <div className="text-center mb-12 animate-fade-in-up delay-100 relative z-10 max-w-2xl mx-auto">
                <h1 className="text-5xl md:text-7xl font-bold mb-6 text-white leading-tight">
                    Państwa Miasta
                </h1>
                <p className="text-lg md:text-xl text-gray-300 font-light">
                    Stwórz pokój, wyślij kod znajomym i zagrajcie razem. Bez zakładania konta.
                </p>
            </div>

            <form className="w-full max-w-4xl glass-panel p-6 md:p-10 mb-16 animate-fade-in-up delay-200 relative z-10" onSubmit={e => e.preventDefault()}>
                <div className="mb-10 max-w-md mx-auto">
                    <label className="block mb-3 text-sm font-semibold text-gray-300 uppercase tracking-wider text-center">
                        Twój nick w grze
                    </label>
                    <input
                        type="text"
                        value={nick}
                        onChange={(e) => setNickState(e.target.value)}
                        className="w-full p-4 rounded-lg bg-brand-bg/50 border border-brand-border focus:outline-none focus:border-brand-primary focus:ring-2 focus:ring-brand-primary/50 text-center text-xl text-white transition-all duration-300 placeholder-gray-600"
                        placeholder="np. Gracz9106"
                    />
                </div>

                <div className="grid md:grid-cols-2 gap-8 relative">
                    <div className="hidden md:block absolute left-1/2 top-0 bottom-0 w-px bg-brand-border transform -translate-x-1/2"></div>

                    <div className="p-6 md:pr-10 flex flex-col justify-between group">
                        <div>
                            <div className="w-12 h-12 rounded-xl bg-brand-primary/20 text-brand-primary flex items-center justify-center mb-6 text-2xl">
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                                </svg>
                            </div>
                            <h3 className="text-3xl font-display font-semibold mb-3">Stwórz pokój</h3>
                            <p className="text-gray-400 text-base mb-6 font-light">
                                Zaproś znajomych do gry. Otrzymasz kod pokoju, który przekażesz innym graczom.
                            </p>

                            <label className="flex items-center gap-3 cursor-pointer p-4 rounded-xl bg-brand-bg/40 border border-brand-border hover:border-brand-primary/50 transition-colors">
                                <div className="relative flex items-center">
                                    <input
                                        type="checkbox"
                                        checked={isPublic}
                                        onChange={() => setIsPublic(!isPublic)}
                                        className="sr-only peer"
                                    />
                                    <div className="w-11 h-6 bg-gray-700 peer-focus:outline-none rounded-full peer peer-checked:after:translate-x-full peer-checked:after:border-white after:content-[''] after:absolute after:top-[2px] after:left-[2px] after:bg-white after:border-gray-300 after:border after:rounded-full after:h-5 after:w-5 after:transition-all peer-checked:bg-brand-primary"></div>
                                </div>
                                <span className="font-medium text-gray-200">Pokój publiczny</span>
                            </label>
                        </div>

                        <button
                            type="button"
                            onClick={handleCreateRoom}
                            className="mt-8 w-full bg-gradient-to-r from-brand-primary to-brand-primary-hover text-white p-4 rounded-xl font-bold text-lg transition-all duration-300 hover:-translate-y-1 cursor-pointer"
                        >
                            Stwórz pokój
                        </button>
                    </div>

                    <div className="p-6 md:pl-10 flex flex-col justify-between group">
                        <div>
                            <div className="w-12 h-12 rounded-xl bg-brand-accent/20 text-brand-accent flex items-center justify-center mb-6 text-2xl">
                                <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6">
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M18 18.72a9.094 9.094 0 0 0 3.741-.479 3 3 0 0 0-4.682-2.72m.94 3.198.001.031c0 .225-.012.447-.037.666A11.944 11.944 0 0 1 12 21c-2.17 0-4.207-.576-5.963-1.584A6.062 6.062 0 0 1 6 18.719m12 0a5.971 5.971 0 0 0-.941-3.197m0 0A5.995 5.995 0 0 0 12 12.75a5.995 5.995 0 0 0-5.058 2.772m0 0a3 3 0 0 0-4.681 2.72 8.986 8.986 0 0 0 3.74.477m.94-3.197a5.971 5.971 0 0 0-.94 3.197M15 6.75a3 3 0 1 1-6 0 3 3 0 0 1 6 0Zm6 3a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Zm-13.5 0a2.25 2.25 0 1 1-4.5 0 2.25 2.25 0 0 1 4.5 0Z" />
                                </svg>
                            </div>
                            <h3 className="text-3xl font-display font-semibold mb-3">Dołącz do gry</h3>
                            <p className="text-gray-400 text-base mb-6 font-light">
                                Wpisz kod pokoju, który otrzymałeś od znajomego.
                            </p>

                            <div className="relative">
                                <input
                                    type="text"
                                    value={roomCode}
                                    onChange={(e) => setRoomCode(e.target.value.toUpperCase())}
                                    className="w-full p-4 rounded-xl bg-brand-bg/40 border border-brand-border focus:outline-none focus:border-brand-accent focus:ring-2 focus:ring-brand-accent/50 text-center font-display text-2xl tracking-widest uppercase transition-all duration-300 placeholder-gray-600"
                                    placeholder="XYZ123"
                                    maxLength={6}
                                />
                            </div>
                        </div>

                        <button
                            type="button"
                            onClick={() => handleJoinRoom(roomCode)}
                            className="mt-8 w-full bg-brand-accent  text-white p-4 rounded-xl font-bold text-lg transition-all duration-300 hover:-translate-y-1 cursor-pointer"
                        >
                            Dołącz teraz
                        </button>
                    </div>
                </div>
            </form>

            <div className="w-full max-w-4xl animate-fade-in-up delay-300 relative z-10">
                <div className="flex items-center justify-between mb-6 px-2">
                    <h2 className="text-2xl font-display font-bold flex items-center gap-3">
                        <span className="w-3 h-3 rounded-full bg-green-500 animate-pulse"></span>
                        Otwarte pokoje
                    </h2>
                </div>

                {publicRooms.length === 0 ? (
                    <div className="text-center text-gray-500 py-8">Brak publicznych pokoi. Stwórz swój własny!</div>
                ) : (
                    <div className="grid sm:grid-cols-2 gap-4">
                        {publicRooms.map(room => (
                            <div key={room.code} className="glass-panel p-5 flex justify-between items-center group hover:border-brand-accent/50 transition-colors">
                                <div>
                                    <div className="flex items-center gap-2 mb-1">
                                        <span className="font-bold text-lg">Pokój {room.code}</span>
                                    </div>
                                    <div className="text-sm text-gray-400 flex items-center gap-4">
                                        <span>Host: {room.hostNick}</span>
                                        <span>👥 {room.playersCount}/{room.maxPlayers}</span>
                                    </div>
                                </div>
                                <button 
                                    onClick={() => handleJoinRoom(room.code)}
                                    className="bg-brand-surface hover:bg-brand-accent hover:text-white border border-brand-border hover:border-brand-accent px-5 py-2 rounded-lg font-medium transition-all duration-300 shadow-sm"
                                >
                                    Wejdź
                                </button>
                            </div>
                        ))}
                    </div>
                )}
            </div>
        </div>
    );
};

export default Home;