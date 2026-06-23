import type { Player, AnswersState, VotesState } from "../../types";

interface ReviewPanelProps {
    categories: string[];
    players: Player[];
    playerId: string;
    gameAnswers: AnswersState;
    gameVotes: VotesState;
    isHost: boolean;
    currentRound: number;
    totalRounds: number;
    onVote: (targetPlayerId: string, category: string, isValid: boolean) => void;
    onNextRound: () => void;
}

export const ReviewPanel = ({ 
    categories, players, playerId, gameAnswers, gameVotes,
    isHost, currentRound, totalRounds, onVote, onNextRound 
}: ReviewPanelProps) => {
    return (
        <div className="animate-fade-in-up">
            <h2 className="text-3xl font-display font-bold text-center mb-8">Odpowiedzi Graczy</h2>
            
            <div className="space-y-6">
                {categories.map((cat: string) => (
                    <div key={cat} className="glass-panel overflow-hidden">
                        <div className="bg-brand-surface p-4 border-b border-brand-border">
                            <h3 className="text-xl font-bold text-brand-accent">{cat}</h3>
                        </div>
                        <div className="p-4 flex flex-col gap-3">
                            {players.map((p: Player) => {
                                const playerAnswer = gameAnswers?.[p.id]?.[cat] || '';
                                const isMe = p.id === playerId;
                                
                                // calc votes
                                const categoryVotes = gameVotes?.[p.id]?.[cat] || {};
                                let positive = 0;
                                let negative = 0;
                                Object.values(categoryVotes).forEach(v => v ? positive++ : negative++);
                                const myVote = categoryVotes[playerId!];
                                
                                return (
                                    <div key={p.id} className={`flex justify-between items-center p-3 rounded-lg border ${
                                        isMe ? 'bg-brand-primary/10 border-brand-primary/30' : 'bg-brand-bg/40 border-brand-border'
                                    }`}>
                                        <div className="flex flex-col">
                                            <span className={`font-medium ${isMe ? 'text-gray-300' : 'text-gray-400'}`}>
                                                {isMe ? `Ty (${p.nick})` : p.nick}
                                            </span>
                                            <span className={`font-bold text-lg ${playerAnswer ? 'text-white' : 'text-gray-500 italic'}`}>
                                                {playerAnswer || 'Brak odpowiedzi'}
                                            </span>
                                        </div>
                                        
                                        {!isMe && playerAnswer && (
                                            <div className="flex items-center gap-2">
                                                <div className="text-sm font-bold mr-2 text-right tracking-widest">
                                                    {positive > 0 && <span className="text-green-400">+{positive}</span>}
                                                    {positive > 0 && negative > 0 && <span className="text-gray-500 mx-1">/</span>}
                                                    {negative > 0 && <span className="text-red-400">-{negative}</span>}
                                                </div>
                                                <button 
                                                    onClick={() => onVote(p.id, cat, true)}
                                                    className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all ${myVote === true ? 'bg-green-500 text-white shadow-[0_0_10px_rgba(34,197,94,0.5)]' : 'bg-brand-surface border border-brand-border text-gray-400 hover:text-green-400 hover:border-green-500/50'}`}
                                                >
                                                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="size-6">
                                                        <path strokeLinecap="round" strokeLinejoin="round" d="m4.5 12.75 6 6 9-13.5" />
                                                    </svg>

                                                </button>
                                                <button 
                                                    onClick={() => onVote(p.id, cat, false)}
                                                    className={`w-10 h-10 rounded-xl flex items-center justify-center transition-all ${myVote === false ? 'bg-red-500 text-white shadow-[0_0_10px_rgba(239,68,68,0.5)]' : 'bg-brand-surface border border-brand-border text-gray-400 hover:text-red-400 hover:border-red-500/50'}`}
                                                >
                                                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="size-6">
                                                        <path strokeLinecap="round" strokeLinejoin="round" d="M6 18 18 6M6 6l12 12" />
                                                    </svg>
                                                </button>
                                            </div>
                                        )}
                                    </div>
                                )
                            })}
                        </div>
                    </div>
                ))}
            </div>
            
            <div className="mt-8 flex justify-center">
                {isHost ? (
                    <button onClick={onNextRound} className="px-10 py-4 bg-brand-surface hover:bg-brand-primary hover:border-brand-primary border border-brand-border text-white rounded-full font-bold text-lg transition-all duration-300 shadow-sm">
                        {currentRound < totalRounds ? "Następna runda" : "Zakończ grę"}
                    </button>
                ) : (
                    <div className="text-gray-400 italic font-medium">Oczekiwanie aż host przejdzie dalej...</div>
                )}
            </div>
        </div>
    );
};
