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

app.get('/dashboard', async (req, res) => {
    try {
        const { rows } = await pool.query(
            `SELECT
                COUNT(*)                        AS users,
                SUM(edit)                       AS edit_total,
                SUM(batch)                      AS batch_total,
                SUM(game)                       AS game_total,
                SUM(online)                     AS online_total,
                SUM(replay)                     AS replay_total,
                AVG(edit)                       AS edit_avg,
                AVG(batch)                      AS batch_avg,
                AVG(game)                       AS game_avg,
                AVG(online)                     AS online_avg,
                AVG(replay)                     AS replay_avg,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY edit)   AS edit_med,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY batch)  AS batch_med,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY game)   AS game_med,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY online) AS online_med,
                PERCENTILE_CONT(0.5) WITHIN GROUP (ORDER BY replay) AS replay_med,
                MAX(edit)                       AS edit_max,
                MAX(batch)                      AS batch_max,
                MAX(game)                       AS game_max,
                MAX(online)                     AS online_max,
                MAX(replay)                     AS replay_max
             FROM usage_stats`
        );

        const d = rows[0];
        const users = parseInt(d.users);

        function hm(minutes) {
            const m = Math.round(parseFloat(minutes) || 0);
            return `${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`;
        }

        const screens = [
            { label: 'BotEdit',  total: d.edit_total,   avg: d.edit_avg,   med: d.edit_med,   max: d.edit_max   },
            { label: 'Batch',    total: d.batch_total,  avg: d.batch_avg,  med: d.batch_med,  max: d.batch_max  },
            { label: 'Game',     total: d.game_total,   avg: d.game_avg,   med: d.game_med,   max: d.game_max   },
            { label: 'Online',   total: d.online_total, avg: d.online_avg, med: d.online_med, max: d.online_max },
            { label: 'Replay',   total: d.replay_total, avg: d.replay_avg, med: d.replay_med, max: d.replay_max },
        ];

        const rows_html = screens.map(s => `
            <tr>
                <td>${s.label}</td>
                <td>${hm(s.total)}</td>
                <td>${hm(s.avg)}</td>
                <td>${hm(s.med)}</td>
                <td>${hm(s.max)}</td>
            </tr>`).join('');

        res.send(`<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>Orbito Stats</title>
<style>
  body { font-family: monospace; background: #111; color: #ddd; padding: 40px; }
  h1   { font-size: 18px; letter-spacing: 4px; color: #fff; margin-bottom: 24px; }
  p    { color: #888; margin: 4px 0 20px; font-size: 13px; }
  table { border-collapse: collapse; min-width: 480px; }
  th, td { padding: 10px 20px; text-align: right; border-bottom: 1px solid #2a2a2a; }
  th { color: #666; font-size: 11px; letter-spacing: 1px; }
  td:first-child, th:first-child { text-align: left; color: #aaa; }
</style>
</head>
<body>
<h1>ORBITO STATS</h1>
<p>총 유저 수: ${users}</p>
<table>
  <thead>
    <tr>
      <th>Screen</th>
      <th>총 누적</th>
      <th>유저 평균</th>
      <th>유저 중간값</th>
      <th>유저 최댓값</th>
    </tr>
  </thead>
  <tbody>${rows_html}</tbody>
</table>
</body>
</html>`);
    } catch (err) {
        console.error(err);
        res.status(500).send('db error');
    }
});

const PORT = process.env.PORT || 3000;
app.listen(PORT, () => console.log(`orbit-server running on :${PORT}`));
