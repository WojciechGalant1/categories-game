import { useState, useEffect } from "react";
import type { Room } from "../types";

export const useGameTimer = (room: Room | null) => {
    const [displayTimeLeft, setDisplayTimeLeft] = useState<number | undefined>();

    useEffect(() => {
        if (room?.status === "playing" && room?.game?.mainTimeLeft !== undefined) {
            setDisplayTimeLeft(room.game.mainTimeLeft);
        } else if (room?.status !== "playing") {
            setDisplayTimeLeft(undefined);
        }
    }, [room?.game?.mainTimeLeft, room?.status, room?.game?.currentRound]);

    useEffect(() => {
        if (room?.status !== "playing") {
            return;
        }
        const id = window.setInterval(() => {
            setDisplayTimeLeft((t) => (t !== undefined && t > 0 ? t - 1 : t ?? 0));
        }, 1000);
        return () => clearInterval(id);
    }, [room?.status, room?.game?.currentRound]);

    return { displayTimeLeft };
};
