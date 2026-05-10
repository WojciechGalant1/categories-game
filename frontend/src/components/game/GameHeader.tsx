interface GameHeaderProps {
    currentRound: number;
    totalRounds: number;
    currentLetter: string;
    phase: string;
    mainTimeLeft?: number;
}

export const GameHeader = ({ currentRound, totalRounds, currentLetter, phase, mainTimeLeft }: GameHeaderProps) => {
    return (
        <div className="w-full flex justify-center mb-10 relative z-10">
            <div className="flex w-full max-w-4xl items-center justify-between p-4">
                
                {/* LEFT: Round Number */}
                <div className="flex-1 flex items-center justify-start">
                    <div className="flex flex-col">
                        <span className="text-gray-400 text-xs md:text-sm uppercase tracking-widest font-semibold mb-1">Runda</span>
                        <div className="text-2xl md:text-3xl font-bold font-display text-white">
                            <span className="text-brand-accent">{currentRound}</span>
                            <span className="text-gray-600 mx-2">/</span>
                            <span className="text-gray-400">{totalRounds}</span>
                        </div>
                    </div>
                </div>
                
                {/* CENTER: Letter */}
                <div className="flex-1 flex justify-center items-center gap-3 md:gap-4">
                    <span className="text-xl md:text-2xl font-medium hidden sm:block">Litera: {currentLetter}</span>
                </div>

                {/* RIGHT: Time Left */}
                <div className="flex-1 flex justify-end">
                    {phase === 'playing' && mainTimeLeft !== undefined ? (
                        <div className="flex flex-col items-end">
                            <div className="flex items-center gap-2 md:gap-3 px-3 py-1 md:px-4 md:py-2 rounded-xl shadow-inner">
                                <svg xmlns="http://www.w3.org/2000/svg" className="h-5 w-5 md:h-6 md:w-6 text-brand-accent animate-pulse" fill="none" viewBox="0 0 24 24" stroke="currentColor" strokeWidth={2}>
                                    <path strokeLinecap="round" strokeLinejoin="round" d="M12 8v4l3 3m6-3a9 9 0 11-18 0 9 9 0 0118 0z" />
                                </svg>
                                <div className="text-2xl md:text-4xl font-display font-bold text-white tabular-nums tracking-wider">
                                    {Math.floor(mainTimeLeft / 60).toString().padStart(2, '0')}:{(mainTimeLeft % 60).toString().padStart(2, '0')}
                                </div>
                            </div>
                        </div>
                    ) : <div />}
                </div>

            </div>
        </div>
    );
};
