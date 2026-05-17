/* ════════════════════════════════════════════════════════════════════════════
   SKYDELAY · LAUNCHER · app.js
   - Generates the starfield
   - Drives the live UTC clock + uptime counter
   - Pings :7070 and :8080 to update health badges
   - Pulls live counters from the existing REST APIs
   - Wires the shutdown modal
   - Card glow follows cursor (mouse-tracked accent)
   ════════════════════════════════════════════════════════════════════════════ */

(() => {

    /* ── Constants ────────────────────────────────────────────────────────── */
    //   Port layout (v2.0.0):
    //     7070 → Mission Control Launcher (this UI)
    //     8080 → Public Flight Map
    //     9090 → Business Dashboard (licensed)
    const PORTS = {
        launcher:  7070,
        map:       8080,
        dashboard: 9090
    };

    const LICENSE_KEY = 'admin123';

    const ENDPOINTS = {
        airports:    `http://localhost:${PORTS.map}/api/airports`,
        flights:     `http://localhost:${PORTS.dashboard}/api/flight-features`,
        predictions: `http://localhost:${PORTS.map}/api/predictions`,
        weather:     `http://localhost:${PORTS.dashboard}/api/weather-records?limit=10000`,
        evaluation:  `http://localhost:${PORTS.dashboard}/api/model-evaluation`,
        mapHealth:   `http://localhost:${PORTS.map}/api/airports`,
        dashHealth:  `http://localhost:${PORTS.dashboard}/api/airports`,
        pdf:         '/docs/user_guide.pdf'
    };

    /* ── Starfield generator ──────────────────────────────────────────────── */
    function buildStarfield() {
        const root = document.getElementById('bg-stars');
        if (!root) return;
        const count = Math.min(120, Math.floor((window.innerWidth * window.innerHeight) / 11000));
        const frag = document.createDocumentFragment();
        for (let i = 0; i < count; i++) {
            const s = document.createElement('span');
            s.className = 'star' + (Math.random() > 0.85 ? ' lg' : '');
            s.style.left = `${Math.random() * 100}%`;
            s.style.top  = `${Math.random() * 100}%`;
            s.style.animationDelay = `${(Math.random() * 4).toFixed(2)}s`;
            s.style.animationDuration = `${(2.5 + Math.random() * 3.5).toFixed(2)}s`;
            if (Math.random() > 0.75) s.style.opacity = 0.85;
            frag.appendChild(s);
        }
        root.appendChild(frag);
    }

    /* ── UTC clock + uptime ───────────────────────────────────────────────── */
    const launchedAt = Date.now();

    function pad(n) { return String(n).padStart(2, '0'); }
    function fmtTime(d) { return `${pad(d.getUTCHours())}:${pad(d.getUTCMinutes())}:${pad(d.getUTCSeconds())}`; }
    function fmtElapsed(ms) {
        const s = Math.floor(ms / 1000);
        return `${pad(Math.floor(s / 3600))}:${pad(Math.floor((s % 3600) / 60))}:${pad(s % 60)}`;
    }

    function tickClock() {
        const utc = document.getElementById('utc-clock');
        const up  = document.getElementById('uptime-clock');
        if (utc) utc.textContent = fmtTime(new Date());
        if (up)  up.textContent  = fmtElapsed(Date.now() - launchedAt);
    }

    /* ── Health pings ─────────────────────────────────────────────────────── */
    function setHealth(id, ok, lblOk = 'ONLINE', lblBad = 'OFFLINE') {
        const wrap = document.getElementById(id);
        if (!wrap) return;
        const dot  = wrap.querySelector('.card-health-dot');
        const text = wrap.querySelector('.card-health-text');
        if (dot)  dot.classList.toggle('ok', !!ok),  dot.classList.toggle('bad', !ok);
        if (text) {
            text.classList.toggle('ok', !!ok); text.classList.toggle('bad', !ok);
            text.textContent = ok ? lblOk : lblBad;
        }
    }

    function setStatusRow(key, ok, label) {
        const row = document.querySelector(`.status-row[data-key="${key}"]`);
        if (!row) return;
        const dot   = row.querySelector('.status-row-dot');
        const state = row.querySelector('.status-row-state');
        if (dot) {
            dot.classList.toggle('ok', !!ok);
            dot.classList.toggle('bad', !ok);
        }
        if (state) {
            state.classList.toggle('ok', !!ok);
            state.classList.toggle('bad', !ok);
            state.textContent = label;
        }
    }

    async function ping(url, timeout = 1800) {
        const ctl = new AbortController();
        const t = setTimeout(() => ctl.abort(), timeout);
        try {
            const r = await fetch(url, { signal: ctl.signal, cache: 'no-store' });
            return r.ok || (r.status >= 200 && r.status < 500);
        } catch { return false; }
        finally { clearTimeout(t); }
    }

    async function pingExists(url) {
        try {
            const r = await fetch(url, { method: 'HEAD', cache: 'no-store' });
            return r.ok;
        } catch { return false; }
    }

    /* ── Hero stats ───────────────────────────────────────────────────────── */
    function setStat(id, value) {
        const el = document.getElementById(id);
        if (!el) return;
        if (typeof value === 'number') {
            animateNumber(el, parseInt(el.dataset.cur || '0', 10), value, 900);
            el.dataset.cur = value;
        } else {
            el.textContent = value;
        }
    }

    function animateNumber(el, from, to, duration) {
        const start = performance.now();
        const step = (t) => {
            const p = Math.min(1, (t - start) / duration);
            const ease = 1 - Math.pow(1 - p, 3);
            const v = Math.round(from + (to - from) * ease);
            el.textContent = v.toLocaleString();
            if (p < 1) requestAnimationFrame(step);
        };
        requestAnimationFrame(step);
    }

    async function loadHeroStats() {
        // Airports — best effort: from either service
        try {
            let airports = [];
            const tryUrls = [ENDPOINTS.airports, ENDPOINTS.dashHealth];
            for (const u of tryUrls) {
                try {
                    const r = await fetch(u, { cache: 'no-store' });
                    if (r.ok) { airports = await r.json(); break; }
                } catch {/*continue*/}
            }
            const n = Array.isArray(airports) ? airports.length : (airports && airports.length) || 0;
            setStat('stat-airports', n || 0);
        } catch { setStat('stat-airports', '—'); }

        // Historic flights
        try {
            const r = await fetch(ENDPOINTS.flights, { cache: 'no-store' });
            if (r.ok) {
                const data = await r.json();
                const n = Array.isArray(data) ? data.length : 0;
                setStat('stat-flights', n);
            } else setStat('stat-flights', '—');
        } catch { setStat('stat-flights', '—'); }

        // Live predictions
        try {
            const r = await fetch(ENDPOINTS.predictions, { cache: 'no-store' });
            if (r.ok) {
                const data = await r.json();
                const n = Array.isArray(data) ? data.length : 0;
                setStat('stat-predictions', n);
            } else setStat('stat-predictions', '—');
        } catch { setStat('stat-predictions', '—'); }

        // Weather records
        try {
            const r = await fetch(ENDPOINTS.weather, { cache: 'no-store' });
            if (r.ok) {
                const data = await r.json();
                const n = Array.isArray(data) ? data.length : 0;
                setStat('stat-weather', n);
            } else setStat('stat-weather', '—');
        } catch { setStat('stat-weather', '—'); }
    }

    /* ── Status checks ────────────────────────────────────────────────────── */
    async function refreshHealth() {
        const stamp = new Date();
        const refresh = document.getElementById('last-refresh');
        if (refresh) refresh.textContent = `last check ${fmtTime(stamp)} UTC`;

        const [mapUp, dashUp, pdfOk] = await Promise.all([
            ping(ENDPOINTS.mapHealth),
            ping(ENDPOINTS.dashHealth),
            pingExists(ENDPOINTS.pdf)
        ]);

        setHealth('health-8080', mapUp);
        setHealth('health-9090', dashUp);
        setHealth('health-docs', pdfOk, 'AVAILABLE', 'NOT FOUND');

        setStatusRow('map',  mapUp,  mapUp  ? 'ONLINE' : 'OFFLINE');
        setStatusRow('dash', dashUp, dashUp ? 'ONLINE' : 'OFFLINE');

        // Model + datamart presumed OK if dashboard responds — proxy-check
        let modelOk = false, datamartOk = false, ingestOk = false;
        if (dashUp) {
            try {
                const r = await fetch(ENDPOINTS.evaluation, { cache: 'no-store' });
                modelOk = (r.status === 200);
            } catch {}
            try {
                const r = await fetch(ENDPOINTS.flights, { cache: 'no-store' });
                datamartOk = r.ok;
            } catch {}
        }
        if (mapUp) {
            try {
                const r = await fetch(ENDPOINTS.predictions, { cache: 'no-store' });
                ingestOk = r.ok;
            } catch {}
        }
        setStatusRow('model',    modelOk,    modelOk    ? 'READY'    : 'TRAINING');
        setStatusRow('datamart', datamartOk, datamartOk ? 'CONNECTED' : 'UNREACHABLE');
        setStatusRow('ingest',   ingestOk,   ingestOk   ? 'STREAMING' : 'WAITING');

        // Global chip
        const chip = document.getElementById('global-status');
        if (chip) {
            const allOk = mapUp && dashUp;
            chip.style.background  = allOk ? 'rgba(52,211,153,0.08)'  : 'rgba(251,191,36,0.08)';
            chip.style.borderColor = allOk ? 'rgba(52,211,153,0.30)'  : 'rgba(251,191,36,0.30)';
            const dot  = chip.querySelector('.status-dot');
            const text = chip.querySelector('.status-text');
            if (dot)  dot.style.background = allOk ? '#34d399' : '#fbbf24';
            if (text) {
                text.style.color    = allOk ? '#34d399' : '#fbbf24';
                text.textContent    = allOk ? 'ALL SYSTEMS NOMINAL' : 'PARTIAL DEGRADATION';
            }
        }
    }

    /* ── Card hover glow follows mouse ────────────────────────────────────── */
    function wireCardGlow() {
        document.querySelectorAll('.action-card').forEach(card => {
            card.addEventListener('mousemove', (e) => {
                const r = card.getBoundingClientRect();
                const x = ((e.clientX - r.left) / r.width)  * 100;
                const y = ((e.clientY - r.top)  / r.height) * 100;
                card.style.setProperty('--mx', `${x}%`);
                card.style.setProperty('--my', `${y}%`);
            });
        });
    }

    /* ── Shutdown modal ───────────────────────────────────────────────────── */
    function wireShutdown() {
        const back   = document.getElementById('shutdown-modal');
        const open   = document.getElementById('btn-shutdown');
        const cancel = document.getElementById('btn-cancel-shutdown');
        const confirm = document.getElementById('btn-confirm-shutdown');

        if (open)   open.addEventListener('click', () => back && back.classList.add('visible'));
        if (cancel) cancel.addEventListener('click', () => back && back.classList.remove('visible'));
        if (back)   back.addEventListener('click', (e) => { if (e.target === back) back.classList.remove('visible'); });

        if (confirm) confirm.addEventListener('click', async () => {
            confirm.disabled = true;
            confirm.textContent = 'TERMINATING…';
            try {
                await fetch('/api/shutdown', { method: 'POST' });
            } catch (e) {/* expected — server is dying */}
            setTimeout(() => {
                document.body.innerHTML =
                    `<div style="
                        height:100vh;display:grid;place-items:center;background:#03060f;color:#94a3b8;
                        font-family:'Space Grotesk',sans-serif;text-align:center;padding:24px;">
                        <div>
                            <div style="font-size:42px;margin-bottom:12px;">✈</div>
                            <div style="font-size:20px;font-weight:600;color:#e2e8f0;letter-spacing:.02em;">SkyDelay has shut down.</div>
                            <div style="margin-top:10px;font-size:13px;letter-spacing:.06em;">You can close this window now.</div>
                        </div>
                    </div>`;
            }, 900);
        });
    }

    /* ── Password gate (Business Dashboard) ───────────────────────────────── */
    function wireAuthGate() {
        const modal   = document.getElementById('auth-modal');
        const form    = document.getElementById('auth-form');
        const input   = document.getElementById('auth-input');
        const error   = document.getElementById('auth-error');
        const cancel  = document.getElementById('btn-cancel-auth');
        if (!modal || !form || !input) return;

        let pendingUrl = null;

        // Intercept every action-card click that requires auth
        document.querySelectorAll('.action-card[data-requires-auth="true"]').forEach(card => {
            card.addEventListener('click', (e) => {
                e.preventDefault();
                e.stopPropagation();
                pendingUrl = card.getAttribute('href');
                openAuth();
            });
        });

        function openAuth() {
            error.textContent = '';
            error.classList.remove('visible');
            input.value = '';
            modal.classList.add('visible');
            setTimeout(() => input.focus(), 60);
        }

        function closeAuth() {
            modal.classList.remove('visible');
            input.value = '';
            error.textContent = '';
            error.classList.remove('visible');
            pendingUrl = null;
        }

        cancel.addEventListener('click', closeAuth);
        modal.addEventListener('click', (e) => { if (e.target === modal) closeAuth(); });
        document.addEventListener('keydown', (e) => {
            if (e.key === 'Escape' && modal.classList.contains('visible')) closeAuth();
        });

        form.addEventListener('submit', (e) => {
            e.preventDefault();
            const entered = (input.value || '').trim();
            if (entered === LICENSE_KEY) {
                error.textContent = '';
                error.classList.remove('visible');
                const url = pendingUrl;
                closeAuth();
                if (url) window.open(url, '_blank', 'noopener');
            } else {
                error.textContent = '✕  ACCESS DENIED — invalid license key';
                error.classList.add('visible');
                input.classList.remove('shake');
                // force reflow so the animation restarts on subsequent failures
                void input.offsetWidth;
                input.classList.add('shake');
                input.select();
            }
        });
    }

    /* ── Manual refresh ───────────────────────────────────────────────────── */
    function wireRefresh() {
        const btn = document.getElementById('btn-refresh');
        if (!btn) return;
        btn.addEventListener('click', async () => {
            btn.style.pointerEvents = 'none';
            const icon = btn.querySelector('svg');
            if (icon) icon.style.animation = 'radar-spin 0.8s linear';
            await Promise.all([refreshHealth(), loadHeroStats()]);
            setTimeout(() => {
                if (icon) icon.style.animation = '';
                btn.style.pointerEvents = '';
            }, 800);
        });
    }

    /* ── Boot ─────────────────────────────────────────────────────────────── */
    function boot() {
        buildStarfield();
        tickClock();
        setInterval(tickClock, 1000);

        wireCardGlow();
        wireAuthGate();
        wireShutdown();
        wireRefresh();

        // Initial pulls
        refreshHealth();
        loadHeroStats();

        // Periodic refresh
        setInterval(refreshHealth, 8000);
        setInterval(loadHeroStats, 30000);
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', boot);
    } else {
        boot();
    }
})();
