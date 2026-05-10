interface AnswerFormProps {
    categories: string[];
    currentLetter: string;
    answers: Record<string, string>;
    onAnswerChange: (category: string, value: string) => void;
    onSubmit: () => void;
    hasSubmitted: boolean;
}

export const AnswerForm = ({ categories, currentLetter, answers, onAnswerChange, onSubmit, hasSubmitted }: AnswerFormProps) => {
    return (
        <div className="glass-panel p-6 md:p-8 animate-fade-in-up delay-100">
            <div className="space-y-6">
                {categories.map((cat: string, idx: number) => (
                    <div key={cat} className="flex flex-col md:flex-row md:items-center gap-2 md:gap-6">
                        <label className="md:w-1/3 text-lg font-medium text-gray-200">
                            {idx + 1}. {cat}
                        </label>
                        <input
                            type="text"
                            value={answers[cat] || ''}
                            onChange={(e) => onAnswerChange(cat, e.target.value)}
                            className="flex-1 p-4 rounded-xl bg-brand-bg/50 border border-brand-border focus:outline-none focus:border-brand-primary focus:ring-2 focus:ring-brand-primary/50 text-white text-lg transition-all disabled:opacity-60 disabled:cursor-not-allowed"
                            placeholder={`Słowo na '${currentLetter}'...`}
                            autoComplete="off"
                        />
                    </div>
                ))}
            </div>

            {!hasSubmitted ? (
                <div className="mt-10 pt-6 border-t border-brand-border flex justify-center">
                    <button
                        onClick={onSubmit}
                        className="px-12 py-4 bg-red-600 hover:from-red-500 hover:to-red-400 text-white rounded-full font-display font-bold text-xl tracking-widest uppercase transition-all duration-300 hover:scale-105"
                    >
                        Wyślij odpowiedzi
                    </button>
                </div>
            ) : (
                <div className="mt-10 pt-6 border-t border-brand-border flex justify-center text-gray-400 italic font-medium">
                    Oczekiwanie na pozostałych graczy...
                </div>
            )}
        </div>
    );
};
