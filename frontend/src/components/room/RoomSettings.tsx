import { CATEGORIES } from "../../constants/game";

interface RoomSettingsProps {
    settings: {
        categories: string[];
        timePerRound: number;
        rounds: number;
    };
    isHost: boolean;
    toggleCategory: (category: string) => void;
    updateTime: (t: number) => void;
    updateRounds: (change: number) => void;
    handleStartGame: () => void;
}

export const RoomSettings = ({ 
    settings, 
    isHost, 
    toggleCategory, 
    updateTime, 
    updateRounds, 
    handleStartGame 
}: RoomSettingsProps) => {
    return (
        <div className="glass-panel p-6 md:p-8 flex flex-col h-full">
            <div className="mb-8 border-b border-brand-border pb-4">
                <h2 className="text-2xl font-display font-semibold flex items-center gap-2">
                    <svg xmlns="http://www.w3.org/2000/svg" fill="none" viewBox="0 0 24 24" strokeWidth={1.5} stroke="currentColor" className="w-6 h-6 text-brand-secondary">
                        <path strokeLinecap="round" strokeLinejoin="round" d="M9.594 3.94c.09-.542.56-.94 1.11-.94h2.593c.55 0 1.02.398 1.11.94l.213 1.281c.063.374.313.686.645.87.074.04.147.083.22.127.325.196.72.257 1.075.124l1.217-.456a1.125 1.125 0 0 1 1.37.49l1.296 2.247a1.125 1.125 0 0 1-.26 1.431l-1.003.827c-.293.241-.438.613-.43.992a7.723 7.723 0 0 1 0 .255c-.008.378.137.75.43.99l1.005.828c.424.35.534.955.26 1.43l-1.298 2.247a1.125 1.125 0 0 1-1.369.491l-1.217-.456c-.355-.133-.75-.072-1.076.124a6.47 6.47 0 0 1-.22.128c-.331.183-.581.495-.644.869l-.213 1.281c-.09.543-.56.94-1.11.94h-2.594c-.55 0-1.019-.398-1.11-.94l-.213-1.281c-.062-.374-.312-.686-.644-.87a6.52 6.52 0 0 1-.22-.127c-.325-.196-.72-.257-1.076-.124l-1.217.456a1.125 1.125 0 0 1-1.369-.49l-1.297-2.247a1.125 1.125 0 0 1 .26-1.431l1.004-.827c.292-.24.437-.613.43-.991a6.932 6.932 0 0 1 0-.255c.007-.38-.138-.751-.43-.992l-1.004-.827a1.125 1.125 0 0 1-.26-1.43l1.297-2.247a1.125 1.125 0 0 1 1.37-.491l1.216.456c.356.133.751.072 1.076-.124.072-.044.146-.086.22-.128.332-.183.582-.495.644-.869l.214-1.28Z" />
                        <path strokeLinecap="round" strokeLinejoin="round" d="M15 12a3 3 0 1 1-6 0 3 3 0 0 1 6 0Z" />
                    </svg>
                    Ustawienia Gry {!isHost && <span className="text-sm font-normal text-gray-400 ml-2">(tylko host)</span>}
                </h2>
            </div>

            <div className="mb-8">
                <label className="block mb-4 text-sm font-semibold text-gray-300 uppercase tracking-wider">
                    Wybrane Kategorie ({settings.categories?.length || 0})
                </label>
                <div className="flex flex-wrap gap-3">
                    {CATEGORIES.map(cat => {
                        const isSelected = settings.categories?.includes(cat);
                        return (
                            <button
                                key={cat}
                                type="button"
                                onClick={() => toggleCategory(cat)}
                                disabled={!isHost}
                                className={`px-4 py-2 rounded-xl text-sm font-medium transition-all duration-300 border ${
                                    isSelected 
                                    ? "bg-brand-primary/20 border-brand-primary text-white" 
                                    : "bg-brand-surface border-brand-border text-gray-400 hover:text-white hover:border-gray-500"
                                } ${!isHost && 'cursor-default opacity-80'}`}
                            >
                                {isSelected && <span className="mr-2 text-brand-primary">✓</span>}
                                {cat}
                            </button>
                        )
                    })}
                </div>
            </div>

            <div className="grid sm:grid-cols-2 gap-8 mb-8">
                <div>
                    <label className="block mb-4 text-sm font-semibold text-gray-300 uppercase tracking-wider">
                        Czas na rundę
                    </label>
                    <div className="flex gap-3">
                        {[1, 2, 5].map(t => (
                            <button
                                key={t}
                                type="button"
                                onClick={() => updateTime(t)}
                                disabled={!isHost}
                                className={`flex-1 py-3 rounded-xl font-medium transition-all duration-300 border ${
                                    settings.timePerRound === t
                                    ? "bg-brand-secondary/20 border-brand-secondary text-white"
                                    : "bg-brand-surface border-brand-border text-gray-400 hover:bg-brand-surface-hover"
                                } ${!isHost && 'cursor-default'}`}
                            >
                                {t} min
                            </button>
                        ))}
                    </div>
                </div>

                <div>
                    <label className="block mb-4 text-sm font-semibold text-gray-300 uppercase tracking-wider">
                        Liczba rund
                    </label>
                    <div className="flex items-center gap-4 bg-brand-surface border border-brand-border rounded-xl p-2">
                        <button 
                            type="button"
                            onClick={() => updateRounds(-1)}
                            disabled={!isHost}
                            className="w-10 h-10 flex items-center justify-center rounded-lg bg-brand-bg hover:bg-gray-800 text-white transition-colors disabled:opacity-50"
                        >
                            -
                        </button>
                        <div className="flex-1 text-center font-display text-2xl font-bold text-white">
                            {settings.rounds}
                        </div>
                        <button 
                            type="button"
                            onClick={() => updateRounds(1)}
                            disabled={!isHost}
                            className="w-10 h-10 flex items-center justify-center rounded-lg bg-brand-bg hover:bg-gray-800 text-white transition-colors disabled:opacity-50"
                        >
                            +
                        </button>
                    </div>
                </div>
            </div>

            <div className="mt-auto pt-6">
                {isHost ? (
                    <button
                        type="button"
                        onClick={handleStartGame}
                        className="w-full bg-brand-accent text-white py-5 rounded-xl font-bold text-xl uppercase tracking-widest transition-all duration-300 transform hover:-translate-y-1 relative overflow-hidden group"
                    >
                        <div className="absolute inset-0 w-full h-full bg-white/20 transform hover:-translate-y-1 cursor-pointer"></div>
                        Rozpocznij grę
                    </button>
                ) : (
                    <div className="w-full bg-brand-surface border border-brand-border text-gray-400 py-5 rounded-xl font-bold text-xl uppercase tracking-widest text-center">
                        Oczekiwanie na hosta...
                    </div>
                )}
            </div>

        </div>
    );
};
