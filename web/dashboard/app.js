/* ============================================
   SKYDELAY — app.js  v3
   Flight Delay Intelligence Dashboard
   Historical data only — powered by flight_features datamart
   ============================================ */

(() => {
    'use strict';

    // ─── CURATED TOP AIRPORTS (representative major Spanish airports) ───
    const TOP_ICAO = [
        'LEMD', // Madrid
        'LEBL', // Barcelona
        'LEPA', // Palma de Mallorca
        'LEMG', // Málaga
        'LEAL', // Alicante
        'LEVC', // Valencia
        'LEZL', // Sevilla
        'GCLP', // Gran Canaria
        'GCXO', // Tenerife Norte
        'GCTS', // Tenerife Sur
        'LEBB', // Bilbao
        'LEIB', // Ibiza
        'LEST', // Santiago
        'LEAS', // Asturias
    ];

    // ─── THEME (light default) ───────────────────────────────
    let isDark = false;

    const DARK = {
        cyan: '#00d4ff', cyanDim: 'rgba(0,212,255,0.12)', cyanMid: 'rgba(0,212,255,0.25)',
        red: '#ff4d6a', redDim: 'rgba(255,77,106,0.12)',
        warn: '#f59e0b', warnDim: 'rgba(245,158,11,0.12)',
        safe: '#10b981', safeDim: 'rgba(16,185,129,0.12)',
        violet: '#7c3aed', violetDim: 'rgba(124,58,237,0.12)',
        text: '#e2e8f8', textMuted: '#4a6080',
        grid: 'rgba(0,212,255,0.05)', border: 'rgba(0,212,255,0.07)',
        tooltipBg: '#040c1a', tooltipBorder: 'rgba(0,212,255,0.2)',
    };
    const LIGHT = {
        cyan: '#0891b2', cyanDim: 'rgba(8,145,178,0.10)', cyanMid: 'rgba(8,145,178,0.20)',
        red: '#e11d48', redDim: 'rgba(225,29,72,0.10)',
        warn: '#d97706', warnDim: 'rgba(217,119,6,0.10)',
        safe: '#059669', safeDim: 'rgba(5,150,105,0.10)',
        violet: '#7c3aed', violetDim: 'rgba(124,58,237,0.10)',
        text: '#1e293b', textMuted: '#64748b',
        grid: 'rgba(0,0,0,0.05)', border: 'rgba(0,0,0,0.08)',
        tooltipBg: '#ffffff', tooltipBorder: 'rgba(0,0,0,0.12)',
    };
    let T = LIGHT;

    function applyChartDefaults() {
        Chart.defaults.color = T.textMuted;
        Chart.defaults.font.family = "'Fira Code', monospace";
        Chart.defaults.font.size = 11;
        Chart.defaults.plugins.tooltip.backgroundColor = T.tooltipBg;
        Chart.defaults.plugins.tooltip.borderColor = T.tooltipBorder;
        Chart.defaults.plugins.tooltip.borderWidth = 1;
        Chart.defaults.plugins.tooltip.padding = 10;
        Chart.defaults.plugins.tooltip.titleColor = T.text;
        Chart.defaults.plugins.tooltip.bodyColor = T.textMuted;
        Chart.defaults.plugins.tooltip.titleFont = { family: "'Space Grotesk', sans-serif", size: 13, weight: '700' };
        Chart.defaults.plugins.tooltip.bodyFont = { family: "'Fira Code', monospace", size: 11 };
        Chart.defaults.plugins.legend.labels.color = T.textMuted;
        Chart.defaults.plugins.legend.labels.padding = 16;
    }

    // ─── HELPERS ─────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
    const fmtMin = m => m === 0 ? '0 min' : (m > 0 ? `+${Math.round(m)} min` : `${Math.round(m)} min`);

    function pearson(x, y) {
        const n = Math.min(x.length, y.length);
        if (n < 2) return 0;
        let mx = 0, my = 0;
        for (let i = 0; i < n; i++) { mx += x[i]; my += y[i]; }
        mx /= n; my /= n;
        let num = 0, dx = 0, dy = 0;
        for (let i = 0; i < n; i++) {
            const a = x[i] - mx, b = y[i] - my;
            num += a * b; dx += a * a; dy += b * b;
        }
        const den = Math.sqrt(dx * dy);
        return den === 0 ? 0 : num / den;
    }

    function delayCategory(min) {
        if (min <= 15) return 'none';
        if (min <= 30) return 'low';
        if (min <= 60) return 'moderate';
        return 'severe';
    }

    function pickAircraftBy(km) {
        if (km > 1500) return 'Airbus A321 Neo';
        if (km > 800)  return 'Airbus A320 Neo';
        if (km > 400)  return 'Airbus A320';
        return 'ATR 72-600';
    }

    // ─── AIRPORTS (loaded from API) ──────────────────────────
    let AIRPORTS = [];

    // ─── DATA STATE ──────────────────────────────────────────
    let DATA = { flights: [], weatherCache: {}, useReal: false };

    // ─── FETCH ───────────────────────────────────────────────
    async function loadAirports() {
        try {
            const res = await fetch('/api/airports');
            if (res.ok) {
                const list = await res.json();
                if (Array.isArray(list) && list.length > 0) {
                    AIRPORTS = list.map(a => ({
                        icao: a.icao, iata: a.iata,
                        name: a.name || a.iata,
                        lat: a.lat, lon: a.lon
                    })).sort((a, b) => a.iata.localeCompare(b.iata));
                }
            }
        } catch (e) { console.warn('Airports API unavailable:', e.message); }
    }

    async function loadFlights() {
        try {
            const res = await fetch('/api/flight-features');
            if (res.ok) {
                const features = await res.json();
                if (Array.isArray(features) && features.length > 0) {
                    DATA.flights = enrichFromFeatures(features);
                    DATA.useReal = true;
                }
            }
        } catch (e) { console.warn('Flight API unavailable:', e.message); }
    }

    async function loadWeatherFor(icao) {
        if (DATA.weatherCache[icao]) return DATA.weatherCache[icao];
        try {
            const res = await fetch(`/api/weather-series?icao=${icao}&limit=200`);
            if (res.ok) {
                const wx = await res.json();
                if (Array.isArray(wx) && wx.length > 0) {
                    DATA.weatherCache[icao] = wx;
                    return wx;
                }
            }
        } catch (e) { /* silent */ }
        return [];
    }

    function findAirport(icao) {
        return AIRPORTS.find(a => a.icao === icao) || { iata: icao || '???', icao: icao || '????', name: icao || '???' };
    }

    function enrichFromFeatures(rows) {
        return rows.map((r, i) => {
            const ori = findAirport(r.originIcao);
            const dst = findAirport(r.destIcao);
            const cat = r.delayCategory || delayCategory(r.departureDelay);
            const delay = r.departureDelay || 0;
            let status = delay <= 15 ? 'On Time' : delay > 120 ? 'Cancelled' : 'Delayed';

            let hour = null, dateStr = null;
            if (r.scheduledDeparture) {
                const d = new Date(r.scheduledDeparture);
                if (!isNaN(d.getTime())) { hour = d.getUTCHours(); dateStr = r.scheduledDeparture.slice(0, 10); }
            }

            return {
                id: i + 1,
                flightId: r.flightId || '—',
                airline: r.flightId?.split(' ')[0] || 'Unknown',
                aircraft: r.aircraftModel || pickAircraftBy(r.distanceKm),
                origin: ori, destination: dst,
                distanceKm: r.distanceKm || 0,
                depDelay: delay, category: cat, status,
                hour, dateStr,
                weather: { temp: r.temp || 0, wind: r.wind || 0, gust: r.gust || 0, vis: (r.vis || 10000) / 1000 }
            };
        });
    }

    // ─── DYNAMIC SELECTORS ───────────────────────────────────
    function populateAirportSelect(selectId, includeAll) {
        const sel = $(selectId);
        if (!sel) return;
        const current = sel.value;
        sel.innerHTML = '';
        if (includeAll) {
            const opt = document.createElement('option');
            opt.value = ''; opt.textContent = 'All airports';
            sel.appendChild(opt);
        }
        AIRPORTS.forEach(a => {
            const opt = document.createElement('option');
            opt.value = a.icao;
            opt.textContent = `${a.iata} — ${a.name}`;
            sel.appendChild(opt);
        });
        if (current) sel.value = current;
    }

    function populateFlightOriginFilter() {
        const sel = $('filter-origin');
        if (!sel) return;
        const current = sel.value;
        sel.innerHTML = '<option value="">All</option>';
        const origins = new Set(DATA.flights.map(f => f.origin.iata));
        AIRPORTS.filter(a => origins.has(a.iata)).forEach(a => {
            const opt = document.createElement('option');
            opt.value = a.iata;
            opt.textContent = `${a.iata} — ${a.name}`;
            sel.appendChild(opt);
        });
        if (current) sel.value = current;
    }

    // ─── CHART INSTANCES ─────────────────────────────────────
    let chartInstances = {};
    function destroyChart(key) { if (chartInstances[key]) { chartInstances[key].destroy(); delete chartInstances[key]; } }
    function destroyAllCharts() { Object.keys(chartInstances).forEach(destroyChart); chartsBuilt = { overview: false, airports: false, weather: false, flights: false }; }

    // ================================================================
    //  ① OTP Bar — Curated ~14 major Spanish airports only
    // ================================================================
    function buildOTPBar() {
        destroyChart('otpBar');
        const ctx = $('chart-otp-bar');
        if (!ctx) return;

        const data = TOP_ICAO.map(icao => {
            const a = findAirport(icao);
            const fs = DATA.flights.filter(f => f.origin.icao === icao);
            const onTime = fs.filter(f => f.depDelay <= 15).length;
            const otp = fs.length ? (onTime / fs.length) * 100 : -1;
            return { iata: a.iata, name: a.name, otp, count: fs.length };
        }).filter(d => d.count > 0).sort((a, b) => a.otp - b.otp);

        const colors = data.map(d => d.otp >= 80 ? T.safe : d.otp >= 60 ? T.warn : T.red);
        const bgColors = data.map(d => d.otp >= 80 ? T.safeDim : d.otp >= 60 ? T.warnDim : T.redDim);

        chartInstances.otpBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.iata),
                datasets: [{ label: 'On-Time %', data: data.map(d => +d.otp.toFixed(1)),
                    backgroundColor: bgColors, borderColor: colors, borderWidth: 1.5, borderRadius: 4 }]
            },
            options: {
                indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: c => { const d = data[c.dataIndex]; return ` OTP: ${d.otp.toFixed(1)}% · ${d.count} flights`; } } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + '%' }, min: 0, max: 100 },
                    y: { grid: { color: T.grid }, ticks: { color: T.text, font: { weight: '700' } } }
                }
            }
        });
    }

    // ================================================================
    //  ② Feature Impact Tornado + Pearson explainer
    // ================================================================
    function buildFeatureImpact() {
        destroyChart('featureImpact');
        const ctx = $('chart-feature-impact');
        if (!ctx) return;

        const delays = DATA.flights.map(f => f.depDelay);
        const features = [
            { label: 'Wind speed',  key: f => f.weather.wind },
            { label: 'Wind gust',   key: f => f.weather.gust },
            { label: 'Visibility',  key: f => -f.weather.vis },
            { label: 'Temperature', key: f => Math.abs(f.weather.temp - 18) },
            { label: 'Distance',    key: f => f.distanceKm },
        ];
        const corrs = features.map(feat => {
            const xs = DATA.flights.map(feat.key);
            return { label: feat.label, r: pearson(xs, delays) };
        }).sort((a, b) => Math.abs(b.r) - Math.abs(a.r));

        const colors = corrs.map(c => { const a = Math.abs(c.r); return a > 0.35 ? T.red : a > 0.18 ? T.warn : T.cyan; });
        const dims   = colors.map(c => c === T.red ? T.redDim : c === T.warn ? T.warnDim : T.cyanDim);

        chartInstances.featureImpact = new Chart(ctx, {
            type: 'bar',
            data: { labels: corrs.map(c => c.label),
                datasets: [{ label: 'Correlation r', data: corrs.map(c => +c.r.toFixed(3)),
                    backgroundColor: dims, borderColor: colors, borderWidth: 1.5, borderRadius: 4 }] },
            options: {
                indexAxis: 'y', responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: c => ` r = ${c.parsed.x.toFixed(3)} (${Math.abs(c.parsed.x) > 0.35 ? 'strong' : Math.abs(c.parsed.x) > 0.18 ? 'moderate' : 'weak'})` } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, min: -1, max: 1,
                        title: { display: true, text: 'Pearson r', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    // ================================================================
    //  ③ Severity Area — Continuous stacked area by HOUR (all airports)
    // ================================================================
    function buildSeverityArea() {
        destroyChart('severityArea');
        const ctx = $('chart-severity-area');
        if (!ctx) return;

        const flights = DATA.flights.filter(f => f.hour !== null);
        const hours = Array.from({ length: 24 }, (_, i) => i);

        const byHour = hours.map(h => {
            const fs = flights.filter(f => f.hour === h);
            return {
                none: fs.filter(f => f.category === 'none').length,
                low: fs.filter(f => f.category === 'low').length,
                moderate: fs.filter(f => f.category === 'moderate').length,
                severe: fs.filter(f => f.category === 'severe').length,
            };
        });

        const catColors = {
            none:     { bg: isDark ? 'rgba(16,185,129,0.45)' : 'rgba(5,150,105,0.35)',  border: T.safe },
            low:      { bg: isDark ? 'rgba(0,212,255,0.45)'  : 'rgba(8,145,178,0.35)',  border: T.cyan },
            moderate: { bg: isDark ? 'rgba(245,158,11,0.50)' : 'rgba(217,119,6,0.40)',  border: T.warn },
            severe:   { bg: isDark ? 'rgba(255,77,106,0.55)' : 'rgba(225,29,72,0.45)',  border: T.red },
        };

        const cats = ['none', 'low', 'moderate', 'severe'];
        const catDisplayNames = { none: 'On Time', low: 'Low', moderate: 'Moderate', severe: 'Severe' };

        chartInstances.severityArea = new Chart(ctx, {
            type: 'line',
            data: {
                labels: hours.map(h => `${String(h).padStart(2, '0')}:00`),
                datasets: cats.map((c, idx) => ({
                    label: catDisplayNames[c],
                    data: byHour.map(bh => bh[c]),
                    backgroundColor: catColors[c].bg,
                    borderColor: catColors[c].border,
                    borderWidth: 2,
                    fill: idx === 0 ? 'origin' : '-1',
                    tension: 0.4,
                    pointRadius: 0,
                    pointHoverRadius: 4,
                    order: cats.length - idx,
                }))
            },
            options: {
                responsive: true, maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { display: false },
                    tooltip: { callbacks: {
                        title: items => items[0]?.label || '',
                        afterBody: items => {
                            const total = items.reduce((s, it) => s + (it.raw || 0), 0);
                            return `Total: ${total} flights`;
                        }
                    } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y: { stacked: true, grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' flights' },
                         beginAtZero: true }
                }
            }
        });
    }

    // ================================================================
    //  ④ Aircraft Model Donut (no "Unknown")
    // ================================================================
    function buildAircraftDonut() {
        destroyChart('aircraftDonut');
        const ctx = $('chart-aircraft-donut');
        if (!ctx) return;

        const byModel = {};
        DATA.flights.forEach(f => {
            const m = f.aircraft || '';
            if (!m || m === 'Unknown') return;
            if (!byModel[m]) byModel[m] = { count: 0, totalDelay: 0 };
            byModel[m].count++;
            byModel[m].totalDelay += Math.max(0, f.depDelay);
        });

        const models = Object.entries(byModel)
            .map(([name, d]) => ({ name, count: d.count, avg: d.count ? d.totalDelay / d.count : 0 }))
            .sort((a, b) => b.count - a.count);

        if (models.length === 0) return;

        const palette    = [T.cyan, T.warn, T.red, T.safe, T.violet, '#f472b6', '#60a5fa', '#a78bfa', '#fb923c', '#34d399'];
        const dimPalette = palette.map(c => c + '30');

        chartInstances.aircraftDonut = new Chart(ctx, {
            type: 'doughnut',
            data: { labels: models.map(m => m.name),
                datasets: [{ data: models.map(m => m.count),
                    backgroundColor: models.map((_, i) => dimPalette[i % dimPalette.length]),
                    borderColor: models.map((_, i) => palette[i % palette.length]), borderWidth: 2 }] },
            options: {
                responsive: true, maintainAspectRatio: false, cutout: '58%',
                plugins: {
                    legend: { position: 'right', labels: { boxWidth: 12, font: { size: 10 }, padding: 10, color: T.textMuted } },
                    tooltip: { callbacks: { label: c => { const m = models[c.dataIndex]; return ` ${m.count} flights · avg delay ${m.avg.toFixed(1)} min`; } } }
                }
            }
        });
    }

    // ================================================================
    //  ⑤ Airport Performance Bubble — reduced size, offset labels
    // ================================================================
    function buildAirportBubble() {
        destroyChart('airportBubble');
        const ctx = $('chart-airport-bubble');
        if (!ctx) return;

        const data = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.icao === a.icao);
            const count = fs.length;
            if (count === 0) return null;
            const onTime = fs.filter(f => f.depDelay <= 15).length;
            const otp = (onTime / count) * 100;
            const avgDelay = fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / count;
            return { iata: a.iata, name: a.name, count, otp, avgDelay };
        }).filter(Boolean);

        const maxDelay = Math.max(...data.map(d => d.avgDelay), 1);

        // Label plugin with offset to avoid overlap
        const labelPlugin = {
            id: 'bubbleLabels',
            afterDraw(chart) {
                const c = chart.ctx;
                c.save();
                c.font = "bold 9px 'Fira Code', monospace";
                c.textAlign = 'center'; c.fillStyle = T.text;
                const meta = chart.getDatasetMeta(0).data;
                meta.forEach((pt, i) => {
                    const r = data[i] ? clamp(4 + (data[i].avgDelay / maxDelay) * 14, 5, 18) : 8;
                    c.textBaseline = 'bottom';
                    c.fillText(data[i].iata, pt.x, pt.y - r - 3);
                });
                c.restore();
            }
        };

        chartInstances.airportBubble = new Chart(ctx, {
            type: 'bubble',
            data: { datasets: [{ label: 'Airports',
                data: data.map(d => ({ x: d.count, y: d.otp, r: clamp(4 + (d.avgDelay / maxDelay) * 14, 5, 18) })),
                backgroundColor: data.map(d => d.otp >= 80 ? T.safeDim : d.otp >= 60 ? T.warnDim : T.redDim),
                borderColor: data.map(d => d.otp >= 80 ? T.safe : d.otp >= 60 ? T.warn : T.red), borderWidth: 1.5 }] },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: c => { const d = data[c.dataIndex];
                        return [` ${d.name}`, ` OTP: ${d.otp.toFixed(1)}%`, ` Flights: ${d.count}`, ` Avg delay: ${d.avgDelay.toFixed(1)} min`]; } } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted },
                        title: { display: true, text: 'Total flights', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + '%' }, min: 0, max: 105,
                        title: { display: true, text: 'On-Time Performance %', color: T.textMuted, font: { size: 10 } } }
                }
            },
            plugins: [labelPlugin]
        });
    }

    // ================================================================
    //  ⑥ Delays by Hour of Day (with airport filter)
    // ================================================================
    function buildHourBar() {
        destroyChart('hourBar');
        const ctx = $('chart-hour-bar');
        if (!ctx) return;

        const filterIcao = $('hour-airport-filter')?.value || '';
        let flights = DATA.flights.filter(f => f.hour !== null);
        if (filterIcao) flights = flights.filter(f => f.origin.icao === filterIcao);

        const hours = Array.from({ length: 24 }, (_, i) => i);
        const delays = hours.map(h => {
            const fs = flights.filter(f => f.hour === h);
            return fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / fs.length : 0;
        });
        const counts = hours.map(h => flights.filter(f => f.hour === h).length);

        chartInstances.hourBar = new Chart(ctx, {
            type: 'bar',
            data: { labels: hours.map(h => `${String(h).padStart(2, '0')}h`),
                datasets: [{ label: 'Avg Departure Delay',
                    data: delays.map(v => +v.toFixed(1)),
                    backgroundColor: delays.map(v => v > 40 ? T.redDim : v > 25 ? T.warnDim : T.safeDim),
                    borderColor: delays.map(v => v > 40 ? T.red : v > 25 ? T.warn : T.safe),
                    borderWidth: 1.5, borderRadius: 4 }] },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: c => ` ${c.parsed.y} min avg · ${counts[c.dataIndex]} flights` } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' min' } }
                }
            }
        });
    }

    // ================================================================
    //  ⑦ Airport × Severity Heatmap — PAGINATED (main / all)
    // ================================================================
    let heatmapPage = 'main'; // 'main' or 'all'

    function buildAirportSeverityHeatmap() {
        const root = $('heatmap-airport-severity');
        if (!root) return;

        const cats = ['none', 'low', 'moderate', 'severe'];
        const catLabels = { none: 'On Time', low: 'Low', moderate: 'Moderate', severe: 'Severe' };
        const catBarCss  = { none: 'var(--safe)', low: 'var(--cyan)', moderate: 'var(--warn)', severe: 'var(--red)' };

        let html = `<div class="hm-corner"></div>`;
        cats.forEach(c => { html += `<div class="hm-col-head">${catLabels[c]}</div>`; });
        html += `<div class="hm-col-head">Flights</div>`;

        // Filter airports based on current page
        const allAirportData = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.icao === a.icao);
            return { ...a, flights: fs, total: fs.length };
        }).filter(d => d.total > 0).sort((a, b) => b.total - a.total);

        let airportData;
        if (heatmapPage === 'main') {
            airportData = allAirportData.filter(a => TOP_ICAO.includes(a.icao));
        } else {
            airportData = allAirportData.filter(a => !TOP_ICAO.includes(a.icao));
        }

        if (airportData.length === 0) {
            root.innerHTML = '<p style="text-align:center;color:var(--t2);padding:2rem;">No data for this group.</p>';
            return;
        }

        airportData.forEach((a, rowIdx) => {
            const total = a.total;
            const pcts = cats.map(c => total ? a.flights.filter(f => f.category === c).length / total : 0);
            const counts = cats.map(c => a.flights.filter(f => f.category === c).length);
            const cls = rowIdx % 2 === 0 ? 'hm-row-even' : '';

            html += `<div class="hm-row-label ${cls}"><span class="hm-iata">${a.iata}</span><span class="hm-row-name">${a.name}</span></div>`;
            pcts.forEach((p, i) => {
                const barW = Math.max(2, p * 100);
                html += `<div class="hm-cell ${cls}" data-cat="${cats[i]}">
                    <div class="hm-bar-track"><div class="hm-bar-fill" style="width:${barW}%;background:${catBarCss[cats[i]]}"></div></div>
                    <span class="hm-val">${(p * 100).toFixed(0)}%</span>
                    <span class="hm-count">(${counts[i]})</span></div>`;
            });
            html += `<div class="hm-cell hm-total ${cls}"><span class="hm-val">${total}</span></div>`;
        });
        root.innerHTML = html;
    }

    // ================================================================
    //  ⑧ Weather Series with airport selector
    // ================================================================
    let currentWeatherIcao = 'LEMD';

    async function buildWeatherSeries() {
        destroyChart('weatherSeries');
        const ctx = $('chart-weather-series');
        if (!ctx) return;

        const series = await loadWeatherFor(currentWeatherIcao);
        let times, wind, gust, vis, temp;
        if (series.length > 0) {
            const s = [...series].reverse();
            times = s.map(r => r.timestamp ? r.timestamp.slice(11, 16) : '');
            temp  = s.map(r => +(r.temp || 0).toFixed(1));
            wind  = s.map(r => +(r.windSpeed || 0).toFixed(1));
            gust  = s.map(r => +(r.windGust || 0).toFixed(1));
            vis   = s.map(r => +((r.visibility || 0) / 1000).toFixed(1));
        } else { times = ['No data']; wind = [0]; gust = [0]; vis = [0]; temp = [0]; }

        chartInstances.weatherSeries = new Chart(ctx, {
            type: 'line',
            data: { labels: times,
                datasets: [
                    { label: 'Wind (m/s)', data: wind, borderColor: T.cyan, backgroundColor: isDark ? 'rgba(0,212,255,0.05)' : 'rgba(8,145,178,0.06)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y' },
                    { label: 'Gust (m/s)', data: gust, borderColor: T.red, fill: false, tension: 0.4, borderWidth: 2, borderDash: [4, 3], pointRadius: 0, yAxisID: 'y' },
                    { label: 'Visibility (km)', data: vis, borderColor: T.safe, fill: false, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y2' },
                    { label: 'Temp (°C)', data: temp, borderColor: T.warn, backgroundColor: isDark ? 'rgba(245,158,11,0.05)' : 'rgba(217,119,6,0.04)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y3' },
                ] },
            options: {
                responsive: true, maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { labels: { boxWidth: 10, font: { size: 10 } } } },
                scales: {
                    x:  { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y:  { grid: { color: T.grid }, ticks: { color: T.cyan }, position: 'left' },
                    y2: { ticks: { color: T.safe }, position: 'right', grid: { drawOnChartArea: false } },
                    y3: { ticks: { color: T.warn }, position: 'right', grid: { drawOnChartArea: false }, display: false },
                }
            }
        });
    }

    // ================================================================
    //  ⑨ Unified Weather Scatter with variable picker
    // ================================================================
    let currentWeatherVar = 'wind';

    function buildWeatherScatter() {
        destroyChart('weatherScatter');
        const ctx = $('chart-weather-scatter');
        if (!ctx) return;

        const varMap = {
            wind: { key: f => f.weather.wind, label: 'Wind speed (m/s)' },
            gust: { key: f => f.weather.gust, label: 'Wind gust (m/s)' },
            temp: { key: f => f.weather.temp, label: 'Temperature (°C)' },
            vis:  { key: f => f.weather.vis,  label: 'Visibility (km)' },
        };
        const v = varMap[currentWeatherVar] || varMap.wind;
        const MAX_DELAY = 180; // Cap at 3h — above is likely cancelled/diverted
        const pts = DATA.flights
            .filter(f => f.depDelay <= MAX_DELAY)
            .map(f => ({ x: v.key(f), y: Math.max(0, f.depDelay) }));

        chartInstances.weatherScatter = new Chart(ctx, {
            type: 'scatter',
            data: { datasets: [{ label: 'Flight', data: pts,
                pointBackgroundColor: pts.map(p => p.y > 60 ? T.red : p.y > 30 ? T.warn : p.y > 15 ? T.cyan : T.safe),
                pointBorderColor: 'transparent', pointRadius: 3.5, pointHoverRadius: 6 }] },
            options: {
                responsive: true, maintainAspectRatio: false,
                plugins: { legend: { display: false },
                    tooltip: { callbacks: { label: c => ` ${v.label.split(' (')[0]}: ${c.raw.x.toFixed(1)} | Delay: ${c.raw.y} min` } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: v.label, color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Departure Delay (min)', color: T.textMuted, font: { size: 10 } } }
                }
            }
        });
    }

    // ================================================================
    //  ⑩ Chord Diagram — Visibility × Delay Severity (D3)
    // ================================================================
    function buildChordDiagram() {
        const container = $('chord-container');
        if (!container) return;
        container.innerHTML = '';

        const CATS = ['none', 'low', 'moderate', 'severe'];
        const CAT_LABELS = { none: 'On Time', low: 'Low Delay', moderate: 'Moderate', severe: 'Severe' };

        // Visibility range colors (blues/purples)
        const visColors = isDark
            ? ['#60a5fa', '#818cf8', '#a78bfa', '#c084fc', '#e879f9']
            : ['#3b82f6', '#6366f1', '#8b5cf6', '#a855f7', '#d946ef'];
        // Delay category colors
        const catColorMap = {
            none:     isDark ? '#10b981' : '#059669',
            low:      isDark ? '#00d4ff' : '#0891b2',
            moderate: isDark ? '#f59e0b' : '#d97706',
            severe:   isDark ? '#ff4d6a' : '#e11d48',
        };

        // Build adaptive visibility ranges
        const allVis = DATA.flights.map(f => f.weather.vis).sort((a, b) => a - b);
        if (allVis.length === 0) {
            container.innerHTML = '<p style="text-align:center;color:var(--t2);padding:2rem;">No visibility data.</p>';
            return;
        }

        const quantile = (arr, q) => {
            const pos = (arr.length - 1) * q;
            const lo = Math.floor(pos), hi = Math.ceil(pos);
            return lo === hi ? arr[lo] : arr[lo] * (hi - pos) + arr[hi] * (pos - lo);
        };

        const breaks = [...new Set([0, quantile(allVis, 0.25), quantile(allVis, 0.50), quantile(allVis, 0.75), allVis[allVis.length - 1] + 0.1].map(v => +v.toFixed(1)))].sort((a, b) => a - b);

        const VIS_RANGES = [];
        for (let i = 0; i < breaks.length - 1; i++) {
            const lo = breaks[i], hi = breaks[i + 1];
            if (hi - lo < 0.05) continue;
            VIS_RANGES.push({
                label: lo === 0 ? `< ${hi.toFixed(0)} km` : `${lo.toFixed(0)}–${hi.toFixed(0)} km`,
                min: lo, max: hi
            });
        }
        if (VIS_RANGES.length < 2) {
            VIS_RANGES.length = 0;
            VIS_RANGES.push({ label: 'All vis.', min: 0, max: Infinity });
        }

        // Groups: visibility ranges first, then delay categories
        const nVis = VIS_RANGES.length;
        const nCat = CATS.length;
        const n = nVis + nCat;
        const names = [...VIS_RANGES.map(v => v.label), ...CATS.map(c => CAT_LABELS[c])];
        const groupColors = [
            ...VIS_RANGES.map((_, i) => visColors[i % visColors.length]),
            ...CATS.map(c => catColorMap[c])
        ];

        // Build the flow matrix (n×n). Only vis→cat and cat→vis have values.
        const matrix = Array.from({ length: n }, () => new Array(n).fill(0));
        DATA.flights.forEach(f => {
            const vi = VIS_RANGES.findIndex(vr => f.weather.vis >= vr.min && f.weather.vis < vr.max);
            const ci = CATS.indexOf(f.category);
            if (vi >= 0 && ci >= 0) {
                matrix[vi][nVis + ci] += 1;
                matrix[nVis + ci][vi] += 1;
            }
        });

        // Check if matrix is all zeros
        const totalFlow = matrix.flat().reduce((a, b) => a + b, 0);
        if (totalFlow === 0) {
            container.innerHTML = '<p style="text-align:center;color:var(--t2);padding:2rem;">No data to display.</p>';
            return;
        }

        // D3 chord layout
        const width = Math.min(container.clientWidth || 600, 520);
        const height = width;
        const outerRadius = width / 2 - 60;
        const innerRadius = outerRadius - 20;

        const chord = d3.chord()
            .padAngle(0.04)
            .sortSubgroups(d3.descending)
            .sortChords(d3.descending);

        const arc = d3.arc().innerRadius(innerRadius).outerRadius(outerRadius);
        const ribbon = d3.ribbon().radius(innerRadius - 1);

        const chords = chord(matrix);

        const svg = d3.select(container).append('svg')
            .attr('width', width).attr('height', height)
            .append('g')
            .attr('transform', `translate(${width / 2},${height / 2})`);

        // Ribbons
        svg.append('g').selectAll('path')
            .data(chords)
            .join('path')
            .attr('class', 'chord-ribbon')
            .attr('d', ribbon)
            .attr('fill', d => groupColors[d.source.index])
            .attr('stroke', d => {
                const c = d3.color(groupColors[d.source.index]);
                return c ? c.darker(0.3) : '#333';
            })
            .attr('stroke-width', 0.5)
            .style('opacity', 0.6)
            .append('title')
            .text(d => {
                const src = names[d.source.index];
                const tgt = names[d.target.index];
                const val = d.source.value;
                return `${src} → ${tgt}\n${val} flights`;
            });

        // Arcs
        const group = svg.append('g').selectAll('g')
            .data(chords.groups)
            .join('g')
            .attr('class', 'chord-arc');

        group.append('path')
            .attr('d', arc)
            .attr('fill', d => groupColors[d.index])
            .attr('stroke', d => {
                const c = d3.color(groupColors[d.index]);
                return c ? c.darker(0.4) : '#333';
            })
            .style('opacity', 0.85)
            .append('title')
            .text(d => `${names[d.index]}\n${d.value} flights`);

        // Labels
        group.append('text')
            .each(d => { d.angle = (d.startAngle + d.endAngle) / 2; })
            .attr('dy', '0.35em')
            .attr('class', 'chord-label')
            .attr('transform', d => {
                const angle = d.angle * 180 / Math.PI - 90;
                const flip = d.angle > Math.PI;
                return `rotate(${angle}) translate(${outerRadius + 8}) ${flip ? 'rotate(180)' : ''}`;
            })
            .attr('text-anchor', d => d.angle > Math.PI ? 'end' : null)
            .text(d => names[d.index]);

        // Tick marks
        const ticks = group.append('g').selectAll('g')
            .data(d => {
                const k = (d.endAngle - d.startAngle) / d.value;
                const step = d.value > 200 ? Math.ceil(d.value / 4 / 50) * 50 : Math.ceil(d.value / 3 / 10) * 10;
                return d3.range(0, d.value, step).map(v => ({ value: v, angle: v * k + d.startAngle }));
            })
            .join('g')
            .attr('transform', d => `rotate(${d.angle * 180 / Math.PI - 90}) translate(${outerRadius},0)`);

        ticks.append('line')
            .attr('class', 'chord-tick')
            .attr('x2', 4);

        ticks.filter(d => d.value > 0).append('text')
            .attr('class', 'chord-tick-label')
            .attr('x', 6)
            .attr('dy', '0.35em')
            .attr('transform', d => d.angle > Math.PI ? 'rotate(180) translate(-12)' : null)
            .attr('text-anchor', d => d.angle > Math.PI ? 'end' : null)
            .text(d => d.value);
    }

    // ================================================================
    //  ⑪ Flights Table + Calendar Date Filter
    // ================================================================
    let currentPage = 1;
    const PAGE_SIZE = 20;
    let filteredFlights = [];
    let dateRangeStart = null;  // 'YYYY-MM-DD' or null
    let dateRangeEnd   = null;
    let calViewYear, calViewMonth; // calendar navigation state
    let calSelectStep = 0; // 0=nothing, 1=start selected, 2=range complete

    function fmtDate(ds) {
        if (!ds || ds === 'Unknown') return '—';
        // ds is 'YYYY-MM-DD'
        const [y, m, d] = ds.split('-');
        return `${d}/${m}/${y}`;
    }

    function renderFlightsTable() {
        const tbody = $('flights-table-body');
        if (!tbody) return;
        const start = (currentPage - 1) * PAGE_SIZE;
        const slice = filteredFlights.slice(start, start + PAGE_SIZE);
        const totalPages = Math.max(1, Math.ceil(filteredFlights.length / PAGE_SIZE));

        $('flights-count').textContent = `${filteredFlights.length} historical flights${DATA.useReal ? ' · live data' : ' · no data'}`;
        $('page-info').textContent = `${currentPage} / ${totalPages}`;
        $('btn-prev').disabled = currentPage === 1;
        $('btn-next').disabled = currentPage === totalPages || totalPages === 0;

        const sClass = s => ({ 'On Time': 'on-time', 'Delayed': 'delayed', 'Cancelled': 'cancelled' }[s] || '');
        const dClass = d => d > 60 ? 'd-high' : d > 15 ? 'd-mid' : 'd-low';

        tbody.innerHTML = slice.map((f, i) => `<tr>
            <td class="mono" style="color:var(--t2)">${start + i + 1}</td>
            <td><span class="flight-code">${f.flightId}</span></td>
            <td class="mono" style="color:var(--t2);font-size:0.7rem">${fmtDate(f.dateStr)}</td>
            <td style="color:var(--t1)">${f.origin.iata}</td>
            <td style="color:var(--t1)">${f.destination.iata}</td>
            <td><span class="aircraft-badge">${f.aircraft}</span></td>
            <td class="mono" style="color:var(--t2)">${f.distanceKm} km</td>
            <td><span class="delay-val ${dClass(f.depDelay)}">${fmtMin(f.depDelay)}</span></td>
            <td><span class="status-badge ${sClass(f.status)}">${f.status}</span></td></tr>`).join('');
    }

    function applyFlightFilters() {
        const ori = $('filter-origin')?.value || '';
        const sta = $('filter-status')?.value || '';
        filteredFlights = DATA.flights.filter(f => {
            if (ori && f.origin.iata !== ori) return false;
            if (sta && f.status !== sta) return false;
            if (dateRangeStart && f.dateStr && f.dateStr < dateRangeStart) return false;
            if (dateRangeEnd && f.dateStr && f.dateStr > dateRangeEnd) return false;
            if ((dateRangeStart || dateRangeEnd) && !f.dateStr) return false;
            return true;
        });
        currentPage = 1;
        renderFlightsTable();
    }

    // ─── Calendar logic ──────────────────────────
    function getFlightDates() {
        const s = new Set();
        DATA.flights.forEach(f => { if (f.dateStr && f.dateStr !== 'Unknown') s.add(f.dateStr); });
        return s;
    }

    function renderCalendar() {
        const grid = $('cal-grid');
        const label = $('cal-month-label');
        const info = $('cal-range-info');
        if (!grid || !label) return;

        const flightDates = getFlightDates();
        const months = ['January', 'February', 'March', 'April', 'May', 'June',
                        'July', 'August', 'September', 'October', 'November', 'December'];
        label.textContent = `${months[calViewMonth]} ${calViewYear}`;

        // First day of month (0=Sun,1=Mon,...), shift so Mon=0
        const firstDay = new Date(calViewYear, calViewMonth, 1).getDay();
        const startOffset = (firstDay + 6) % 7; // Mon-based
        const daysInMonth = new Date(calViewYear, calViewMonth + 1, 0).getDate();
        const daysInPrev  = new Date(calViewYear, calViewMonth, 0).getDate();
        const today = new Date().toISOString().slice(0, 10);

        let cells = '';
        const totalCells = Math.ceil((startOffset + daysInMonth) / 7) * 7;

        for (let i = 0; i < totalCells; i++) {
            let day, dateStr, isOther = false;
            if (i < startOffset) {
                // previous month
                day = daysInPrev - startOffset + 1 + i;
                const pm = calViewMonth === 0 ? 11 : calViewMonth - 1;
                const py = calViewMonth === 0 ? calViewYear - 1 : calViewYear;
                dateStr = `${py}-${String(pm + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                isOther = true;
            } else if (i >= startOffset + daysInMonth) {
                // next month
                day = i - startOffset - daysInMonth + 1;
                const nm = calViewMonth === 11 ? 0 : calViewMonth + 1;
                const ny = calViewMonth === 11 ? calViewYear + 1 : calViewYear;
                dateStr = `${ny}-${String(nm + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
                isOther = true;
            } else {
                day = i - startOffset + 1;
                dateStr = `${calViewYear}-${String(calViewMonth + 1).padStart(2, '0')}-${String(day).padStart(2, '0')}`;
            }

            const hasData = flightDates.has(dateStr);
            let cls = 'cal-day';
            if (isOther) cls += ' other-month';
            if (dateStr === today) cls += ' today';
            if (!hasData && !isOther) cls += ' no-data';
            if (dateRangeStart && dateRangeEnd && dateStr >= dateRangeStart && dateStr <= dateRangeEnd) cls += ' in-range';
            if (dateStr === dateRangeStart) cls += ' range-start';
            if (dateStr === dateRangeEnd) cls += ' range-end';

            cells += `<button class="${cls}" data-date="${dateStr}" ${!hasData && !isOther ? 'disabled' : ''}>${day}</button>`;
        }
        grid.innerHTML = cells;

        // Info text
        if (dateRangeStart && dateRangeEnd) {
            info.textContent = dateRangeStart === dateRangeEnd
                ? fmtDate(dateRangeStart)
                : `${fmtDate(dateRangeStart)} → ${fmtDate(dateRangeEnd)}`;
        } else if (dateRangeStart) {
            info.textContent = `From ${fmtDate(dateRangeStart)} → click end`;
        } else {
            info.textContent = 'Click to select range';
        }
    }

    function initCalendar() {
        // Start at the most recent flight date
        const dates = [...getFlightDates()].sort();
        const latest = dates.length > 0 ? dates[dates.length - 1] : new Date().toISOString().slice(0, 10);
        const [y, m] = latest.split('-');
        calViewYear = parseInt(y);
        calViewMonth = parseInt(m) - 1;

        // Toggle dropdown
        $('date-range-input')?.addEventListener('click', (e) => {
            e.stopPropagation();
            $('calendar-dropdown')?.classList.toggle('open');
            renderCalendar();
        });

        // Close on outside click
        document.addEventListener('click', (e) => {
            const dd = $('calendar-dropdown');
            const picker = $('date-range-picker');
            if (dd && picker && !picker.contains(e.target)) dd.classList.remove('open');
        });

        // Month nav
        $('cal-prev-month')?.addEventListener('click', (e) => {
            e.stopPropagation();
            calViewMonth--;
            if (calViewMonth < 0) { calViewMonth = 11; calViewYear--; }
            renderCalendar();
        });
        $('cal-next-month')?.addEventListener('click', (e) => {
            e.stopPropagation();
            calViewMonth++;
            if (calViewMonth > 11) { calViewMonth = 0; calViewYear++; }
            renderCalendar();
        });

        // Day click (delegated)
        $('cal-grid')?.addEventListener('click', (e) => {
            const btn = e.target.closest('.cal-day');
            if (!btn || btn.disabled) return;
            const date = btn.dataset.date;

            if (calSelectStep === 0 || calSelectStep === 2) {
                // Start new selection
                dateRangeStart = date;
                dateRangeEnd = null;
                calSelectStep = 1;
            } else {
                // Finish selection
                if (date < dateRangeStart) {
                    dateRangeEnd = dateRangeStart;
                    dateRangeStart = date;
                } else {
                    dateRangeEnd = date;
                }
                calSelectStep = 2;
                updateDateInput();
                applyFlightFilters();
            }
            renderCalendar();
        });

        // Clear button
        $('cal-clear')?.addEventListener('click', (e) => {
            e.stopPropagation();
            dateRangeStart = null;
            dateRangeEnd = null;
            calSelectStep = 0;
            $('date-range-input').value = '';
            $('date-range-input').placeholder = 'All dates';
            applyFlightFilters();
            renderCalendar();
        });
    }

    function updateDateInput() {
        const input = $('date-range-input');
        if (!input) return;
        if (dateRangeStart && dateRangeEnd) {
            input.value = dateRangeStart === dateRangeEnd
                ? fmtDate(dateRangeStart)
                : `${fmtDate(dateRangeStart)} → ${fmtDate(dateRangeEnd)}`;
        } else {
            input.value = '';
            input.placeholder = 'All dates';
        }
    }

    // ─── CLOCK ───────────────────────────────────────────────
    function updateClock() {
        const el = $('live-time');
        const ts = $('footer-ts');
        const now = new Date();
        const str = now.toTimeString().slice(0, 8);
        if (el) el.textContent = str;
        if (ts) ts.textContent = now.toLocaleDateString('en-US', { day: '2-digit', month: 'short', year: 'numeric' }) + ' ' + str;
    }

    // ─── VIEW SWITCHING ──────────────────────────────────────
    let chartsBuilt = { overview: false, airports: false, weather: false, flights: false };

    function activateView(view) {
        document.querySelectorAll('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.view === view));
        document.querySelectorAll('.view-panel').forEach(p => p.classList.toggle('active', p.id === `view-${view}`));

        if (view === 'overview' && !chartsBuilt.overview) {
            buildOTPBar(); buildFeatureImpact(); buildSeverityArea(); buildAircraftDonut();
            chartsBuilt.overview = true;
        }
        if (view === 'airports' && !chartsBuilt.airports) {
            buildAirportBubble(); buildHourBar(); buildAirportSeverityHeatmap();
            chartsBuilt.airports = true;
        }
        if (view === 'weather' && !chartsBuilt.weather) {
            buildWeatherSeries(); buildWeatherScatter(); buildChordDiagram();
            chartsBuilt.weather = true;
        }
        if (view === 'flights' && !chartsBuilt.flights) {
            applyFlightFilters();
            chartsBuilt.flights = true;
        }
    }

    // ─── THEME TOGGLE ────────────────────────────────────────
    function toggleTheme() {
        isDark = !isDark;
        T = isDark ? DARK : LIGHT;
        document.body.classList.toggle('light-mode', !isDark);
        applyChartDefaults();
        $('theme-toggle').textContent = isDark ? '☀️' : '🌙';
        destroyAllCharts();
        activateView(document.querySelector('.nav-btn.active')?.dataset.view || 'overview');
    }

    // ─── INIT ────────────────────────────────────────────────
    async function init() {
        document.body.classList.add('light-mode');
        applyChartDefaults();

        await loadAirports();
        await loadFlights();
        filteredFlights = [...DATA.flights];

        // Populate all dynamic selectors
        populateAirportSelect('hour-airport-filter', true);
        populateAirportSelect('weather-airport-select', false);
        populateFlightOriginFilter();

        // Status chip
        const statusChip = $('data-status');
        if (statusChip) {
            statusChip.textContent = DATA.useReal ? `LIVE · ${DATA.flights.length} flights` : 'NO DATA';
            if (!DATA.useReal) { statusChip.style.color = 'var(--warn)'; statusChip.style.borderColor = 'rgba(245,158,11,0.3)'; statusChip.style.background = 'rgba(245,158,11,0.08)'; }
        }

        // Nav
        document.querySelectorAll('.nav-btn').forEach(btn => btn.addEventListener('click', () => activateView(btn.dataset.view)));

        // Theme
        $('theme-toggle')?.addEventListener('click', toggleTheme);

        // Chart-specific filters
        $('hour-airport-filter')?.addEventListener('change', buildHourBar);
        $('weather-airport-select')?.addEventListener('change', async (e) => { currentWeatherIcao = e.target.value; await buildWeatherSeries(); });
        $('weather-var-select')?.addEventListener('change', (e) => { currentWeatherVar = e.target.value; buildWeatherScatter(); });

        // Heatmap tabs
        document.querySelectorAll('.hm-tab').forEach(tab => {
            tab.addEventListener('click', () => {
                document.querySelectorAll('.hm-tab').forEach(t => t.classList.remove('active'));
                tab.classList.add('active');
                heatmapPage = tab.dataset.hmPage;
                buildAirportSeverityHeatmap();
            });
        });

        // Pagination
        $('btn-prev')?.addEventListener('click', () => { if (currentPage > 1) { currentPage--; renderFlightsTable(); } });
        $('btn-next')?.addEventListener('click', () => { const t = Math.ceil(filteredFlights.length / PAGE_SIZE); if (currentPage < t) { currentPage++; renderFlightsTable(); } });

        // Filters
        ['filter-origin', 'filter-status'].forEach(id => $(id)?.addEventListener('change', applyFlightFilters));

        // Calendar date picker
        initCalendar();

        // Clock
        updateClock(); setInterval(updateClock, 1000);

        // Default view
        activateView('overview');

        // Auto-refresh every 5 minutes
        setInterval(() => {
            console.log('[SkyDelay] Auto-refreshing dashboard data...');
            init();
        }, 5 * 60 * 1000);
    }

    if (document.readyState === 'loading') document.addEventListener('DOMContentLoaded', init);
    else init();
})();
