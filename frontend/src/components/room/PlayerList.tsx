interface Player {
    id: string;
    nick: string;
    isHost: boolean;
}

interface PlayerListProps {
    players: Player[];
    maxPlayers: number;
    playerId: string;
}

export const PlayerList = ({ players, maxPlayers, playerId }: PlayerListProps) => {
    return (
        <div className="glass-panel p-6 h-full flex flex-col">
            <div className="flex items-center justify-between mb-6 border-b border-brand-border pb-4">
                <h2 className="text-2xl font-display font-semibold">Gracze</h2>
                <span className="bg-brand-surface px-3 py-1 rounded-full text-sm font-medium">
                    {players.length} / {maxPlayers}
                </span>
            </div>

            <div className="flex flex-col gap-3 flex-grow">
                {players.map((player) => (
                    <div key={player.id} className="bg-brand-bg/60 border border-brand-primary/30 p-3 rounded-xl flex items-center justify-between group hover:border-brand-primary transition-colors">
                        <div className="flex items-center gap-3">
                            <div className="w-10 h-10 rounded-full bg-gradient-to-br from-brand-primary to-brand-secondary flex items-center justify-center font-bold text-white">
                                {player.nick.charAt(0).toUpperCase()}
                            </div>
                            <span className={`font-medium text-white ${player.id === playerId ? 'underline' : ''}`}>{player.nick}</span>
                        </div>
                        {player.isHost && (
                            <span className="text-xs bg-brand-primary/20 text-brand-primary px-2 py-1 rounded border border-brand-primary/30 uppercase tracking-wider font-semibold">
                                Host
                            </span>
                        )}
                    </div>
                ))}

                {Array.from({ length: maxPlayers - players.length }).map((_, idx) => (
                    <div key={`empty-${idx}`} className="bg-brand-surface/30 border border-dashed border-brand-border p-3 rounded-xl flex items-center gap-3 opacity-50">
                        <div className="w-10 h-10 rounded-full bg-brand-surface flex items-center justify-center text-gray-500">
                            <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={2} stroke="currentColor" className="w-5 h-5">
                                <path strokeLinecap="round" strokeLinejoin="round" d="M12 4.5v15m7.5-7.5h-15" />
                            </svg>
                        </div>
                        <span className="text-gray-400 text-sm">Oczekiwanie na gracza...</span>
                    </div>
                ))}
            </div>
        </div>
    );
};
