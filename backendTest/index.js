const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');
const { MongoClient } = require('mongodb');

const app = express();
const PORT = parseInt(process.env.PORT, 10) || 3000;
const MONGO_URI = process.env.MONGO_URI || 'mongodb://admin:adminpass@localhost:27017/?authSource=admin';
const MONGO_DB = process.env.MONGO_DB || 'stop';

app.use(cors());
app.use(express.json());

let roomsCol = null;

async function connectMongo() {
    const client = new MongoClient(MONGO_URI, { serverSelectionTimeoutMS: 3000 });
    while (true) {
        try {
            await client.connect();
            await client.db('admin').command({ ping: 1 });
            const db = client.db(MONGO_DB);
            roomsCol = db.collection('rooms');
            console.log(`Connected to MongoDB (${MONGO_DB})`);
            return;
        } catch (err) {
            console.error('MongoDB connection failed, retrying in 2s:', err.message);
            await new Promise(r => setTimeout(r, 2000));
        }
    }
}

const loadRoom = (code) => roomsCol.findOne({ _id: code });
const saveRoom = (room) => roomsCol.replaceOne({ _id: room._id }, room, { upsert: true });
const deleteRoom = (code) => roomsCol.deleteOne({ _id: code });

const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');
const generateRoomCode = () => Math.random().toString(36).substring(2, 8).toUpperCase();
const getRandomLetter = () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)];

// Auto-stop timers live only in process memory; with a single backend replica this is fine.
// After a Pod restart an in-progress round will simply stay in "playing" until the host
// clicks the next-round button manually.
function scheduleAutoStop(code, roundIdx, timePerRoundMin) {
    setTimeout(async () => {
        const room = await loadRoom(code);
        if (room && room.status === 'playing' && room.game.currentRound === roundIdx) {
            room.status = 'reviewing';
            await saveRoom(room);
        }
    }, timePerRoundMin * 60 * 1000);
}

// 1. GET /api/rooms - List public rooms in lobby
app.get('/api/rooms', async (req, res) => {
    const cursor = roomsCol.find({ isPublic: true, status: 'lobby' });
    const docs = await cursor.toArray();
    const publicRooms = docs.map(r => ({
        code: r.code,
        hostNick: (r.players.find(p => p.isHost) || {}).nick || 'Nieznany',
        playersCount: r.players.length,
        maxPlayers: r.settings.maxPlayers
    }));
    res.json(publicRooms);
});

// 2. POST /api/rooms - Create a room
app.post('/api/rooms', async (req, res) => {
    const { nick, isPublic } = req.body;
    if (!nick) return res.status(400).json({ error: 'Nick is required' });

    const code = generateRoomCode();
    const hostId = uuidv4();

    const room = {
        _id: code,
        code,
        isPublic: !!isPublic,
        status: 'lobby',
        players: [{ id: hostId, nick, isHost: true }],
        scores: { [hostId]: 0 },
        settings: {
            categories: ["Państwa", "Miasta", "Zwierzęta", "Rośliny", "Imiona"],
            timePerRound: 1,
            rounds: 5,
            maxPlayers: 8
        },
        game: {
            currentRound: 0,
            currentLetter: '',
            roundStartedAt: null,
            stopTriggeredAt: null,
            answers: {},
            votes: {},
            stoppedPlayers: []
        }
    };

    await saveRoom(room);
    res.json({ code, playerId: hostId });
});

// 3. POST /api/rooms/:code/join - Join room
app.post('/api/rooms/:code/join', async (req, res) => {
    const { code } = req.params;
    const { nick } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.status !== 'lobby') return res.status(400).json({ error: 'Game already started' });
    if (room.players.length >= room.settings.maxPlayers) return res.status(400).json({ error: 'Room is full' });
    if (!nick) return res.status(400).json({ error: 'Nick is required' });

    const existingPlayer = room.players.find(p => p.nick === nick);
    if (existingPlayer) {
        return res.json({ code, playerId: existingPlayer.id });
    }

    const playerId = uuidv4();
    room.players.push({ id: playerId, nick, isHost: false });
    room.scores[playerId] = 0;
    await saveRoom(room);

    res.json({ code, playerId });
});

// 4. GET /api/rooms/:code - Get room state (POLLING)
app.get('/api/rooms/:code', async (req, res) => {
    const { code } = req.params;
    const room = await loadRoom(code);
    if (!room) return res.status(404).json({ error: 'Room not found' });

    let mainTimeLeft = 0;
    if (room.status === 'playing' && room.game.roundStartedAt) {
        const elapsed = Date.now() - room.game.roundStartedAt;
        const maxTimeMs = room.settings.timePerRound * 60 * 1000;
        mainTimeLeft = Math.max(0, Math.floor((maxTimeMs - elapsed) / 1000));
    }

    res.json({ ...room, game: { ...room.game, timeLeft: 0, mainTimeLeft } });
});

// 5. POST /api/rooms/:code/settings - Update settings (Host only)
app.post('/api/rooms/:code/settings', async (req, res) => {
    const { code } = req.params;
    const { playerId, settings } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can change settings' });
    if (room.status !== 'lobby') return res.status(400).json({ error: 'Cannot change settings now' });

    room.settings = { ...room.settings, ...settings };
    await saveRoom(room);
    res.json({ success: true });
});

// 6. POST /api/rooms/:code/start - Start game
app.post('/api/rooms/:code/start', async (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can start game' });
    if (room.status !== 'lobby' && room.status !== 'reviewing') return res.status(400).json({ error: 'Cannot start game now' });

    room.status = 'playing';
    room.game.currentRound += 1;
    room.game.currentLetter = getRandomLetter();
    room.game.roundStartedAt = Date.now();
    room.game.stopTriggeredAt = null;
    room.game.answers = {};
    room.game.votes = {};
    room.game.stoppedPlayers = [];

    if (room.game.currentRound === 1) {
        room.scores = {};
        room.players.forEach(p => room.scores[p.id] = 0);
    }

    await saveRoom(room);
    scheduleAutoStop(code, room.game.currentRound, room.settings.timePerRound);

    res.json({ success: true });
});

// 7. POST /api/rooms/:code/stop - Trigger STOP
app.post('/api/rooms/:code/stop', async (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.status !== 'playing') return res.status(400).json({ error: 'Not in playing phase' });
    if (!room.players.find(p => p.id === playerId)) return res.status(403).json({ error: 'Player not in room' });

    if (!room.game.stoppedPlayers) room.game.stoppedPlayers = [];

    if (!room.game.stoppedPlayers.includes(playerId)) {
        room.game.stoppedPlayers.push(playerId);
    }

    if (room.status === 'playing' && room.game.stoppedPlayers.length === room.players.length) {
        room.status = 'reviewing';
    }

    await saveRoom(room);
    res.json({ success: true });
});

// 8. POST /api/rooms/:code/answers - Submit answers
app.post('/api/rooms/:code/answers', async (req, res) => {
    const { code } = req.params;
    const { playerId, answers } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });

    room.game.answers[playerId] = answers || {};

    if (room.status === 'stopping' && Object.keys(room.game.answers).length === room.players.length) {
        room.status = 'reviewing';
    }

    await saveRoom(room);
    res.json({ success: true });
});

// 9. POST /api/rooms/:code/vote - Submit a vote
app.post('/api/rooms/:code/vote', async (req, res) => {
    const { code } = req.params;
    const { voterId, targetPlayerId, category, isValid } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.status !== 'reviewing') return res.status(400).json({ error: 'Not in reviewing phase' });
    if (!room.players.find(p => p.id === voterId)) return res.status(403).json({ error: 'Voter not in room' });

    if (!room.game.votes[targetPlayerId]) {
        room.game.votes[targetPlayerId] = {};
    }
    if (!room.game.votes[targetPlayerId][category]) {
        room.game.votes[targetPlayerId][category] = {};
    }
    room.game.votes[targetPlayerId][category][voterId] = isValid;

    await saveRoom(room);
    res.json({ success: true });
});

// 10. POST /api/rooms/:code/next-round - End round / Go to next round
app.post('/api/rooms/:code/next-round', async (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can end the round' });
    if (room.status !== 'reviewing') return res.status(400).json({ error: 'Not in reviewing phase' });

    Object.keys(room.game.answers).forEach(pId => {
        if (room.scores[pId] === undefined) room.scores[pId] = 0;

        const playerAnswers = room.game.answers[pId];
        Object.keys(playerAnswers).forEach(cat => {
            const answer = playerAnswers[cat];
            if (!answer || !answer.trim()) return;

            const votes = (room.game.votes[pId] || {})[cat] || {};
            let pos = 0, neg = 0;
            Object.values(votes).forEach(v => v ? pos++ : neg++);

            const isValid = pos >= neg;

            if (isValid) {
                let isUnique = true;
                let someoneElseAnswered = false;

                Object.keys(room.game.answers).forEach(otherId => {
                    if (otherId === pId) return;
                    const otherAnswer = (room.game.answers[otherId] || {})[cat];

                    if (otherAnswer && otherAnswer.trim()) {
                        const oVotes = (room.game.votes[otherId] || {})[cat] || {};
                        let oPos = 0, oNeg = 0;
                        Object.values(oVotes).forEach(v => v ? oPos++ : oNeg++);
                        if (oPos >= oNeg) {
                            someoneElseAnswered = true;
                            if (otherAnswer.trim().toLowerCase() === answer.trim().toLowerCase()) {
                                isUnique = false;
                            }
                        }
                    }
                });

                if (!someoneElseAnswered) {
                    room.scores[pId] += 15;
                } else if (isUnique) {
                    room.scores[pId] += 10;
                } else {
                    room.scores[pId] += 5;
                }
            }
        });
    });

    let scheduleNext = false;
    if (room.game.currentRound < room.settings.rounds) {
        room.status = 'playing';
        room.game.currentRound += 1;
        room.game.currentLetter = getRandomLetter();
        room.game.roundStartedAt = Date.now();
        room.game.stopTriggeredAt = null;
        room.game.answers = {};
        room.game.votes = {};
        room.game.stoppedPlayers = [];
        scheduleNext = true;
    } else {
        room.status = 'finished';
    }

    await saveRoom(room);
    if (scheduleNext) {
        scheduleAutoStop(code, room.game.currentRound, room.settings.timePerRound);
    }

    res.json({ success: true });
});

// 11. POST /api/rooms/:code/reset - Back to lobby
app.post('/api/rooms/:code/reset', async (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = await loadRoom(code);

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can reset' });

    room.status = 'lobby';
    room.game.currentRound = 0;
    room.game.currentLetter = '';
    room.game.roundStartedAt = null;
    room.game.stopTriggeredAt = null;
    room.game.answers = {};
    room.game.votes = {};
    room.game.stoppedPlayers = [];
    room.players.forEach(p => room.scores[p.id] = 0);

    await saveRoom(room);
    res.json({ success: true });
});

// Top-level error guard so a Mongo blip doesn't crash the process between requests
app.use((err, req, res, next) => {
    console.error('Unhandled error:', err);
    res.status(500).json({ error: 'Internal server error' });
});

connectMongo().then(() => {
    app.listen(PORT, () => {
        console.log(`Server is running on port ${PORT}`);
    });
});
