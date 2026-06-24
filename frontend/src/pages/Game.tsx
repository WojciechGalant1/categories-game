import { useSearchParams } from "react-router-dom";
import { useRoomWebSocket } from "../hooks/useRoomWebSocket";
import { GameHeader } from "../components/game/GameHeader";
import { AnswerForm } from "../components/game/AnswerForm";
import { ReviewPanel } from "../components/game/ReviewPanel";
import { Leaderboard } from "../components/game/Leaderboard";
import { LeaveRoomButton } from "../components/room/LeaveRoomButton";

const Game = () => {
    const [searchParams] = useSearchParams();
    const roomCode = searchParams.get("code") || "";

    const {
        room,
        playerId,
        isHost,
        sessionReady,
        answers,
        displayTimeLeft,
        handleAnswerChange,
        handleStopClick,
        handleNextRound,
        handleVote,
        handleReset,
        handleLeaveRoom,
    } = useRoomWebSocket(roomCode);

    if (!sessionReady || !room || !playerId) {
        return <div className="pt-32 text-center text-white">Ładowanie gry...</div>;
    }

    const phase = room.status;
    const currentLetter = room.game.currentLetter ?? '';
    const categories = room.settings.categories || [];
    const isReviewing = phase === 'reviewing';
    const isFinished = phase === 'finished';

    return (
        <div className="pt-24 pb-24 min-h-screen flex flex-col items-center px-4 relative">
            <div className="absolute top-24 right-4 z-20">
                <LeaveRoomButton onLeave={handleLeaveRoom} />
            </div>
            <div className="absolute top-1/4 left-10 w-64 h-64 bg-brand-primary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-100 hidden md:block z-0 pointer-events-none"></div>
            <div className="absolute top-1/3 right-10 w-72 h-72 bg-brand-secondary mix-blend-multiply filter blur-3xl opacity-20 animate-float delay-500 hidden md:block z-0 pointer-events-none"></div>

            {!isFinished && (
                <GameHeader
                    currentRound={room.game.currentRound}
                    totalRounds={room.settings.rounds}
                    currentLetter={currentLetter}
                    phase={phase}
                    mainTimeLeft={displayTimeLeft ?? room.game.mainTimeLeft}
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