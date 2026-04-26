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
    const { deviceId, account, nickname, edit = 0, batch = 0, game = 0, online = 0, replay = 0, wins = 0, losses = 0 } = req.body;
    if (!deviceId) return res.status(400).json({ error: 'deviceId required' });

    try {
        await pool.query(
            `INSERT INTO usage_stats (device_id, account, nickname, edit, batch, game, online, replay, wins, losses)
             VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
             ON CONFLICT (device_id) DO UPDATE SET
                 account  = EXCLUDED.account,
                 nickname = EXCLUDED.nickname,
                 edit     = EXCLUDED.edit,
                 batch    = EXCLUDED.batch,
                 game     = EXCLUDED.game,
                 online   = EXCLUDED.online,
                 replay   = EXCLUDED.replay,
                 wins     = EXCLUDED.wins,
                 losses   = EXCLUDED.losses`,
            [deviceId, account ?? null, nickname ?? null, edit, batch, game, online, replay, wins, losses]
        );
        res.json({ ok: true });
    } catch (err) {
        console.error(err);
        res.status(500).json({ error: 'db error' });
    }
});

app.get('/dashboard', async (req, res) => {
    try {
        const [aggRes, userRes] = await Promise.all([
            pool.query(
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
            ),
            pool.query(
                `SELECT device_id, account, nickname,
                        edit, batch, game, online, replay, wins, losses,
                        (edit + batch + game + online + replay) AS total
                 FROM usage_stats
                 ORDER BY total DESC`
            )
        ]);

        const d = aggRes.rows[0];
        const users = parseInt(d.users);
        const userRows = userRes.rows;

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

        const sumTotal = screens.reduce((a, s) => a + (parseFloat(s.total) || 0), 0);
        const sumAvg   = screens.reduce((a, s) => a + (parseFloat(s.avg)   || 0), 0);
        const sumMed   = screens.reduce((a, s) => a + (parseFloat(s.med)   || 0), 0);
        const sumMax   = screens.reduce((a, s) => a + (parseFloat(s.max)   || 0), 0);

        const screen_rows_html = screens.map(s => `
            <tr>
                <td>${s.label}</td>
                <td>${hm(s.total)}</td>
                <td>${hm(s.avg)}</td>
                <td>${hm(s.med)}</td>
                <td>${hm(s.max)}</td>
            </tr>`).join('') + `
            <tr class="total-row">
                <td>합계</td>
                <td>${hm(sumTotal)}</td>
                <td>${hm(sumAvg)}</td>
                <td>${hm(sumMed)}</td>
                <td>${hm(sumMax)}</td>
            </tr>`;

        const userDataJson = JSON.stringify(userRows.map(r => ({
            nickname:  r.nickname  || '—',
            account:   r.account   || '—',
            device_id: r.device_id || '—',
            total:     parseInt(r.total)   || 0,
            edit:      parseInt(r.edit)    || 0,
            batch:     parseInt(r.batch)   || 0,
            game:      parseInt(r.game)    || 0,
            online:    parseInt(r.online)  || 0,
            replay:    parseInt(r.replay)  || 0,
            wins:      parseInt(r.wins)    || 0,
            losses:    parseInt(r.losses)  || 0,
        })).map(r => {
            const games = r.wins + r.losses;
            return { ...r, games, winrate: games > 0 ? Math.floor(r.wins / games * 100) : 0 };
        }));

        res.send(`<!DOCTYPE html>
<html lang="ko">
<head>
<meta charset="utf-8">
<title>Orbito Stats</title>
<style>
  body { font-family: monospace; background: #111; color: #ddd; padding: 40px; }
  h1   { font-size: 18px; letter-spacing: 4px; color: #fff; margin-bottom: 24px; }
  h2   { font-size: 13px; letter-spacing: 3px; color: #888; margin: 40px 0 12px; }
  p    { color: #888; margin: 4px 0 20px; font-size: 13px; }
  table { border-collapse: collapse; }
  th, td { padding: 10px 20px; text-align: right; border-bottom: 1px solid #2a2a2a; white-space: nowrap; }
  th { color: #666; font-size: 11px; letter-spacing: 1px; }
  td:first-child, th:first-child { text-align: left; color: #aaa; }
  tr.total-row td { color: #fff; border-top: 1px solid #444; font-weight: bold; }
  th.sortable { cursor: pointer; user-select: none; }
  th.sortable:hover { color: #aaa; }
  th.asc::after  { content: ' ▲'; color: #8af; }
  th.desc::after { content: ' ▼'; color: #8af; }
  #user-table td:first-child { color: #ddd; }
  #user-table td { color: #aaa; }
</style>
</head>
<body>
<h1>ORBITO STATS</h1>
<p>총 유저 수: ${users}</p>
<h2>SCREEN SUMMARY</h2>
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
  <tbody>${screen_rows_html}</tbody>
</table>

<h2>USER LIST</h2>
<table id="user-table">
  <thead>
    <tr>
      <th class="sortable" data-col="nickname" data-type="str">닉네임</th>
      <th class="sortable" data-col="total">총 이용시간</th>
      <th class="sortable" data-col="edit">BotEdit</th>
      <th class="sortable" data-col="batch">Batch</th>
      <th class="sortable" data-col="game">Game</th>
      <th class="sortable" data-col="online">Online</th>
      <th class="sortable" data-col="replay">Replay</th>
      <th class="sortable" data-col="games">게임 횟수</th>
      <th class="sortable" data-col="winrate">승률</th>
      <th class="sortable" data-col="wins">승</th>
      <th class="sortable" data-col="losses">패</th>
      <th class="sortable" data-col="device_id" data-type="str">기기 ID</th>
      <th class="sortable" data-col="account" data-type="str">Account</th>
    </tr>
  </thead>
  <tbody id="user-tbody"></tbody>
</table>

<script>
  const RAW = ${userDataJson};
  let sortCol = 'total';
  let sortAsc = false;

  function hm(m) {
    m = Math.round(m) || 0;
    return String(Math.floor(m / 60)).padStart(2,'0') + ':' + String(m % 60).padStart(2,'0');
  }

  function render() {
    const tbody = document.getElementById('user-tbody');
    tbody.innerHTML = RAW.map(r =>
      '<tr>' +
      '<td>' + r.nickname  + '</td>' +
      '<td>' + hm(r.total)  + '</td>' +
      '<td>' + hm(r.edit)   + '</td>' +
      '<td>' + hm(r.batch)  + '</td>' +
      '<td>' + hm(r.game)   + '</td>' +
      '<td>' + hm(r.online) + '</td>' +
      '<td>' + hm(r.replay) + '</td>' +
      '<td>' + r.games      + '</td>' +
      '<td>' + (r.games > 0 ? r.winrate + '%' : '—') + '</td>' +
      '<td>' + r.wins      + '</td>' +
      '<td>' + r.losses    + '</td>' +
      '<td>' + r.device_id + '</td>' +
      '<td>' + r.account   + '</td>' +
      '</tr>'
    ).join('');

    document.querySelectorAll('#user-table th').forEach(th => {
      th.classList.remove('asc', 'desc');
      if (th.dataset.col === sortCol) th.classList.add(sortAsc ? 'asc' : 'desc');
    });
  }

  function sortBy(col, isStr) {
    if (sortCol === col) { sortAsc = !sortAsc; }
    else { sortCol = col; sortAsc = isStr; }
    RAW.sort((a, b) => {
      const av = a[col], bv = b[col];
      if (isStr) return sortAsc ? av.localeCompare(bv) : bv.localeCompare(av);
      return sortAsc ? av - bv : bv - av;
    });
    render();
  }

  document.querySelectorAll('#user-table th.sortable').forEach(th => {
    th.addEventListener('click', () => sortBy(th.dataset.col, th.dataset.type === 'str'));
  });

  RAW.sort((a, b) => b.total - a.total);
  render();
</script>
</body>
</html>`);
    } catch (err) {
        console.error(err);
        res.status(500).send('db error');
    }
});

const PORT = process.env.PORT || 3000;
pool.query(`ALTER TABLE usage_stats ADD COLUMN IF NOT EXISTS nickname TEXT`)
    .then(() => pool.query(`ALTER TABLE usage_stats ADD COLUMN IF NOT EXISTS wins INTEGER NOT NULL DEFAULT 0`))
    .then(() => pool.query(`ALTER TABLE usage_stats ADD COLUMN IF NOT EXISTS losses INTEGER NOT NULL DEFAULT 0`))
    .catch(err => console.error('migration error:', err))
    .finally(() => app.listen(PORT, () => console.log(`orbit-server running on :${PORT}`)));
