export type RoomStatus = "lobby" | "playing" | "reviewing" | "finished";
export type AnswersState = Record<string, Record<string, string>>;
export type VotesState = Record<string, Record<string, Record<string, boolean>>>;

export interface Player {
    id: string;
    nick: string;
    isHost: boolean;
}

export interface RoomSettings {
    categories: string[];
    timePerRound: number;
    rounds: number;
    maxPlayers: number;
}

export interface GameState {
    currentRound: number;
    currentLetter: string | null;
    roundStartedAt: number | null;
    roundEndsAt: number | null;
    answers: AnswersState;
    votes: VotesState;
    stoppedPlayers: string[];
    timeLeft: number;
    mainTimeLeft: number;
}

export interface Room {
    code: string;
    isPublic: boolean;
    status: RoomStatus;
    players: Player[];
    scores: Record<string, number>;
    settings: RoomSettings;
    game: GameState;
}

// Typ dla pojedynczego pokoju w liście na stronie głównej
export interface PublicRoomSummary {
    code: string;
    hostNick: string;
    playersCount: number;
    maxPlayers: number;
}
