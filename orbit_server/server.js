require('dotenv').config();
const express = require('express');
const { Pool } = require('pg');

const app = express();
app.use(express.json());

const pool = new Pool({
    connectionString: process.env.DATABASE_URL,
    ssl: { rejectUnauthorized: false }
});

app.post('/api/stats', async (req, res) => {
    const { deviceId, account, edit = 0, batch = 0, game = 0, online = 0, replay = 0 } = req.body;
    if (!deviceId) return res.status(400).json({ error: 'deviceId required' });

    try {
        await pool.query(
            `INSERT INTO usage_stats (device_id, account, edit, batch, game, online, replay)
             VALUES ($1, $2, $3, $4, $5, $6, $7)
             ON CONFLICT (device_id) DO UPDATE SET
                 account = EXCLUDED.account,
                 edit    = EXCLUDED.edit,
                 batch   = EXCLUDED.batch,
                 game    = EXCLUDED.game,
                 online  = EXCLUDED.online,
                 replay  = EXCLUDED.replay`,
            [deviceId, account ?? null, edit, batch, game, online, replay]
        );
        res.json({ ok: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: 'db error' });
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`orbit-server running on :${PORT}`));
