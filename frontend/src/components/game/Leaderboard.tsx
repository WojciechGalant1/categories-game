interface LeaderboardProps {
    players: any[];
    scores: Record<string, number>;
    playerId: string;
    totalRounds: number;
    isHost: boolean;
    onReset: () => void;
}

export const Leaderboard = ({ players, scores, playerId, totalRounds, isHost, onReset }: LeaderboardProps) => {
    const sorted = [...players].sort((a: any, b: any) => (scores?.[b.id] || 0) - (scores?.[a.id] || 0));

    return (
        <div className="animate-fade-in-up w-full max-w-2xl mx-auto">
            <div className="glass-panel p-8 text-center mb-8">
                <h2 className="text-4xl font-display font-bold text-brand-primary mb-6">Koniec Gry!</h2>
                <p className="text-gray-400 mb-8 text-lg">Oto wyniki po {totalRounds} rundach:</p>
                
                <div className="flex flex-col gap-4">
                    {sorted.map((p: any, idx: number) => (
                        <div key={p.id} className={`p-5 rounded-xl border flex items-center justify-between ${idx === 0 ? 'bg-gradient-to-r from-yellow-500/10 to-yellow-600/20 border-yellow-500/50 shadow-[0_0_15px_rgba(234,179,8,0.2)]' : 'bg-brand-surface border-brand-border'}`}>
                            <div className="flex items-center gap-4">
                                <span className={`text-3xl font-display font-bold ${idx === 0 ? 'text-yellow-500' : idx === 1 ? 'text-gray-300' : idx === 2 ? 'text-amber-700' : 'text-gray-500'}`}>
                                    #{idx + 1}
                                </span>
                                <span className={`text-xl font-medium ${idx === 0 ? 'text-white' : 'text-gray-300'}`}>
                                    {p.nick} {p.id === playerId ? '(Ty)' : ''}
                                </span>
                            </div>
                            <span className={`text-2xl font-display font-bold ${idx === 0 ? 'text-yellow-400' : 'text-brand-accent'}`}>
                                {scores?.[p.id] || 0} pkt
                            </span>
                        </div>
                    ))}
                </div>
            </div>
            
            <div className="flex justify-center">
                {isHost ? (
                    <button onClick={onReset} className="px-10 py-5 bg-brand-surface border border-brand-border text-white rounded-xl font-bold text-xl transition-all duration-300 shadow-lg hover:shadow-xl hover:scale-105 uppercase tracking-wider">
                        Wróć do poczekalni
                    </button>
                ) : (
                    <div className="text-gray-400 italic font-medium">Oczekiwanie aż host powróci do poczekalni...</div>
                )}
            </div>
        </div>
    );
};
