import { useSearchParams } from "react-router-dom";
import { getPlayerId } from "../services/api";
import { useGamePolling } from "../hooks/useGamePolling";
import { GameHeader } from "../components/game/GameHeader";
import { AnswerForm } from "../components/game/AnswerForm";
import { ReviewPanel } from "../components/game/ReviewPanel";
import { Leaderboard } from "../components/game/Leaderboard";

const Game = () => {
    const [searchParams] = useSearchParams();
    const roomCode = searchParams.get("code") || "";
    const playerId = getPlayerId(roomCode);

    const {
        room,
        answers,
        handleAnswerChange,
        handleStopClick,
        handleNextRound,
        handleVote,
        handleReset,
    } = useGamePolling(roomCode, playerId);

    if (!room) return <div className="pt-32 text-center text-white">Ładowanie gry...</div>;

    const phase = room.status;
    const currentLetter = room.game.currentLetter;
    const categories = room.settings.categories || [];
    const isReviewing = phase === 'reviewing';
    const isFinished = phase === 'finished';
    
    const me = room.players.find((p: any) => p.id === playerId);
    const isHost = me?.isHost;

    return (
        <div className="pt-24 pb-24 min-h-screen flex flex-col items-center px-4 relative">
            <div className="absolute top-1/4 left-10 w-64 h-64 bg-brand-primary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-100 hidden md:block z-0 pointer-events-none"></div>
            <div className="absolute top-1/3 right-10 w-72 h-72 bg-brand-secondary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-500 hidden md:block z-0 pointer-events-none"></div>

            {!isFinished && (
                <GameHeader
                    currentRound={room.game.currentRound}
                    totalRounds={room.settings.rounds}
                    currentLetter={currentLetter}
                    phase={phase}
                    mainTimeLeft={room.game.mainTimeLeft}
                />
            )}

            <div className="w-full max-w-3xl relative z-10">
                {phase === 'playing' && (
                    <AnswerForm
                        categories={categories}
                        currentLetter={currentLetter}
                        answers={answers}
                        onAnswerChange={handleAnswerChange}
                        onSubmit={handleStopClick}
                        hasSubmitted={room.game.stoppedPlayers?.includes(playerId)}
                    />
                )}

                {isReviewing && (
                    <ReviewPanel
                        categories={categories}
                        players={room.players}
                        playerId={playerId!}
                        gameAnswers={room.game.answers}
                        gameVotes={room.game.votes}
                        isHost={isHost}
                        currentRound={room.game.currentRound}
                        totalRounds={room.settings.rounds}
                        onVote={handleVote}
                        onNextRound={handleNextRound}
                    />
                )}

                {isFinished && (
                    <Leaderboard
                        players={room.players}
                        scores={room.scores}
                        playerId={playerId!}
                        totalRounds={room.settings.rounds}
                        isHost={isHost}
                        onReset={handleReset}
                    />
                )}
            </div>
        </div>
    );
};

export default Game;