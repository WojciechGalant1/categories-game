const express = require('express');
const cors = require('cors');
const { v4: uuidv4 } = require('uuid');

const app = express();
const PORT = 3000;

app.use(cors());
app.use(express.json());

// IN-MEMORY STATE
// rooms[code] = Room object
const rooms = {};
const ALPHABET = 'ABCDEFGHIJKLMNOPQRSTUVWXYZ'.split('');

// Helpers
const generateRoomCode = () => Math.random().toString(36).substring(2, 8).toUpperCase();
const getRandomLetter = () => ALPHABET[Math.floor(Math.random() * ALPHABET.length)];

// ENDPOINTS

// 1. GET /api/rooms - List public rooms
app.get('/api/rooms', (req, res) => {
    const publicRooms = Object.values(rooms)
        .filter(r => r.isPublic && r.status === 'lobby')
        .map(r => ({
            code: r.code,
            hostNick: r.players.find(p => p.isHost)?.nick || 'Nieznany',
            playersCount: r.players.length,
            maxPlayers: r.settings.maxPlayers
        }));
    res.json(publicRooms);
});

// 2. POST /api/rooms - Create a room
app.post('/api/rooms', (req, res) => {
    const { nick, isPublic } = req.body;
    if (!nick) return res.status(400).json({ error: 'Nick is required' });

    const code = generateRoomCode();
    const hostId = uuidv4();

    rooms[code] = {
        code,
        isPublic: !!isPublic,
        status: 'lobby', // lobby | playing | stopping | reviewing | finished
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
            answers: {}, // { playerId: { category: answer } }
            votes: {}, // { targetPlayerId: { category: { voterId: boolean } } }
            stoppedPlayers: [] // [playerId]
        }
    };

    res.json({ code, playerId: hostId });
});

// 3. POST /api/rooms/:code/join - Join room
app.post('/api/rooms/:code/join', (req, res) => {
    const { code } = req.params;
    const { nick } = req.body;
    const room = rooms[code];

    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.status !== 'lobby') return res.status(400).json({ error: 'Game already started' });
    if (room.players.length >= room.settings.maxPlayers) return res.status(400).json({ error: 'Room is full' });
    if (!nick) return res.status(400).json({ error: 'Nick is required' });

    // Check if player already exists by nick
    const existingPlayer = room.players.find(p => p.nick === nick);
    if (existingPlayer) {
        return res.json({ code, playerId: existingPlayer.id });
    }

    const playerId = uuidv4();
    room.players.push({ id: playerId, nick, isHost: false });
    room.scores[playerId] = 0;

    res.json({ code, playerId });
});

// 4. GET /api/rooms/:code - Get room state (POLLING)
app.get('/api/rooms/:code', (req, res) => {
    const { code } = req.params;
    const room = rooms[code];
    if (!room) return res.status(404).json({ error: 'Room not found' });
    
    // Calculate main time left if playing
    let mainTimeLeft = 0;
    if (room.status === 'playing' && room.game.roundStartedAt) {
        const elapsed = Date.now() - room.game.roundStartedAt;
        const maxTimeMs = room.settings.timePerRound * 60 * 1000;
        mainTimeLeft = Math.max(0, Math.floor((maxTimeMs - elapsed) / 1000));
    }

    res.json({ ...room, game: { ...room.game, timeLeft: 0, mainTimeLeft } });
});

// 5. POST /api/rooms/:code/settings - Update settings (Host only)
app.post('/api/rooms/:code/settings', (req, res) => {
    const { code } = req.params;
    const { playerId, settings } = req.body;
    const room = rooms[code];

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can change settings' });
    if (room.status !== 'lobby') return res.status(400).json({ error: 'Cannot change settings now' });

    room.settings = { ...room.settings, ...settings };
    res.json({ success: true });
});

// 6. POST /api/rooms/:code/start - Start game
app.post('/api/rooms/:code/start', (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = rooms[code];

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
    
    // Auto-stop logic when time runs out
    const roundIdx = room.game.currentRound;
    setTimeout(() => {
        if (rooms[code] && rooms[code].status === 'playing' && rooms[code].game.currentRound === roundIdx) {
            rooms[code].status = 'reviewing';
        }
    }, room.settings.timePerRound * 60 * 1000);
    
    // Reset scores if starting fresh
    if (room.game.currentRound === 1) {
        room.scores = {};
        room.players.forEach(p => room.scores[p.id] = 0);
    }

    res.json({ success: true });
});

// 7. POST /api/rooms/:code/stop - Trigger STOP
app.post('/api/rooms/:code/stop', (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = rooms[code];

    if (!room) return res.status(404).json({ error: 'Room not found' });
    if (room.status !== 'playing') return res.status(400).json({ error: 'Not in playing phase' });
    if (!room.players.find(p => p.id === playerId)) return res.status(403).json({ error: 'Player not in room' });

    if (!room.game.stoppedPlayers) room.game.stoppedPlayers = [];
    
    if (!room.game.stoppedPlayers.includes(playerId)) {
        room.game.stoppedPlayers.push(playerId);
    }
    
    // If everyone clicked stop, fast-track to reviewing
    if (room.status === 'playing' && room.game.stoppedPlayers.length === room.players.length) {
        room.status = 'reviewing';
    }

    res.json({ success: true });
});

// 8. POST /api/rooms/:code/answers - Submit answers
app.post('/api/rooms/:code/answers', (req, res) => {
    const { code } = req.params;
    const { playerId, answers } = req.body;
    const room = rooms[code];

    if (!room) return res.status(404).json({ error: 'Room not found' });
    
    room.game.answers[playerId] = answers || {};
    
    // Check if everyone answered
    if (room.status === 'stopping' && Object.keys(room.game.answers).length === room.players.length) {
        room.status = 'reviewing';
    }

    res.json({ success: true });
});

// 9. POST /api/rooms/:code/vote - Submit a vote
app.post('/api/rooms/:code/vote', (req, res) => {
    const { code } = req.params;
    const { voterId, targetPlayerId, category, isValid } = req.body;
    const room = rooms[code];

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

    res.json({ success: true });
});

// 9. POST /api/rooms/:code/next-round - End round / Go to next round
app.post('/api/rooms/:code/next-round', (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = rooms[code];

    if (!room) return res.status(404).json({ error: 'Room not found' });
    const player = room.players.find(p => p.id === playerId);
    if (!player || !player.isHost) return res.status(403).json({ error: 'Only host can end the round' });
    if (room.status !== 'reviewing') return res.status(400).json({ error: 'Not in reviewing phase' });

    // Calculate points
    Object.keys(room.game.answers).forEach(pId => {
        if (room.scores[pId] === undefined) room.scores[pId] = 0;
        
        const playerAnswers = room.game.answers[pId];
        Object.keys(playerAnswers).forEach(cat => {
            const answer = playerAnswers[cat];
            if (!answer || !answer.trim()) return;

            // Check votes
            const votes = room.game.votes[pId]?.[cat] || {};
            let pos = 0, neg = 0;
            Object.values(votes).forEach(v => v ? pos++ : neg++);
            
            // Assume valid if no negative votes beat positive votes
            const isValid = pos >= neg;
            
            if (isValid) {
                // Check uniqueness
                let isUnique = true;
                let someoneElseAnswered = false;
                
                Object.keys(room.game.answers).forEach(otherId => {
                    if (otherId === pId) return;
                    const otherAnswer = room.game.answers[otherId]?.[cat];
                    
                    if (otherAnswer && otherAnswer.trim()) {
                        const oVotes = room.game.votes[otherId]?.[cat] || {};
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

    if (room.game.currentRound < room.settings.rounds) {
        // Next round
        room.status = 'playing';
        room.game.currentRound += 1;
        room.game.currentLetter = getRandomLetter();
        room.game.roundStartedAt = Date.now();
        room.game.stopTriggeredAt = null;
        room.game.answers = {};
        room.game.votes = {};
        room.game.stoppedPlayers = [];
        
        // Auto-stop logic when time runs out
        const roundIdx = room.game.currentRound;
        setTimeout(() => {
            if (rooms[code] && rooms[code].status === 'playing' && rooms[code].game.currentRound === roundIdx) {
                rooms[code].status = 'reviewing';
            }
        }, room.settings.timePerRound * 60 * 1000);

    } else {
        // Game over, show results
        room.status = 'finished';
    }

    res.json({ success: true });
});

// 10. POST /api/rooms/:code/reset - Back to lobby
app.post('/api/rooms/:code/reset', (req, res) => {
    const { code } = req.params;
    const { playerId } = req.body;
    const room = rooms[code];

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

    res.json({ success: true });
});

app.listen(PORT, () => {
    console.log(`Server is running on port ${PORT}`);
});