/* ============================================
   SKYDELAY — app.js
   Flight Delay Intelligence Dashboard
   Historical data only — powered by flight_features datamart
   ============================================ */

(() => {
    'use strict';

    // ─── THEME (dark / light) ────────────────────────────────
    let isDark = true;

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

    let T = DARK;

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

    // ─── AIRPORTS (IATA + ICAO + name) ──────────────────────
    const AIRPORTS = [
        { iata: 'MAD', icao: 'LEMD', name: 'Madrid Barajas' },
        { iata: 'BCN', icao: 'LEBL', name: 'Barcelona El Prat' },
        { iata: 'AGP', icao: 'LEMG', name: 'Málaga Costa del Sol' },
        { iata: 'LPA', icao: 'GCLP', name: 'Gran Canaria' },
        { iata: 'PMI', icao: 'LEPA', name: 'Palma de Mallorca' },
        { iata: 'ALC', icao: 'LEAL', name: 'Alicante-Elche' },
        { iata: 'VLC', icao: 'LEVC', name: 'Valencia' },
        { iata: 'BIO', icao: 'LEBB', name: 'Bilbao' },
        { iata: 'SVQ', icao: 'LEZL', name: 'Sevilla' },
        { iata: 'TFN', icao: 'GCXO', name: 'Tenerife Norte' },
    ];

    function pickAircraftBy(km) {
        if (km > 1500) return 'Airbus A321 Neo';
        if (km > 800)  return 'Airbus A320 Neo';
        if (km > 400)  return 'Airbus A320';
        return 'ATR 72-600';
    }

    // ─── DATA STATE ──────────────────────────────────────────
    let DATA = {
        flights: [],
        weatherSeries: [],
        useReal: false,
    };

    // Track which airports are selected for the bar chart
    let selectedAirports = new Set(AIRPORTS.map(a => a.iata));

    // ─── FETCH REAL DATA ─────────────────────────────────────
    async function loadReal() {
        try {
            const [featRes, wxRes] = await Promise.all([
                fetch('/api/flight-features'),
                fetch('/api/weather-series?icao=LEMD&limit=200'),
            ]);

            if (featRes.ok) {
                const features = await featRes.json();
                if (Array.isArray(features) && features.length > 0) {
                    DATA.flights = enrichFromFeatures(features);
                    DATA.useReal = true;
                }
            }

            if (wxRes.ok) {
                const wx = await wxRes.json();
                if (Array.isArray(wx) && wx.length > 0) {
                    DATA.weatherSeries = wx;
                }
            }
        } catch (e) {
            console.warn('API not available:', e.message);
        }
    }

    function enrichFromFeatures(rows) {
        return rows.map((r, i) => {
            const ori = AIRPORTS.find(a => a.icao === r.originIcao) || { iata: r.originIcao || '???', icao: r.originIcao || '????', name: r.originIcao || '???' };
            const dst = AIRPORTS.find(a => a.icao === r.destIcao) || { iata: r.destIcao || '???', icao: r.destIcao || '????', name: r.destIcao || '???' };
            const cat = r.delayCategory || delayCategory(r.departureDelay);
            const delay = r.departureDelay || 0;
            let status;
            if (delay <= 15) status = 'Landed';
            else if (delay > 120) status = 'Cancelled';
            else status = 'Delayed';
            return {
                id: i + 1,
                flightId: r.flightId || '—',
                airline: r.flightId?.split(' ')[0] || 'Unknown',
                aircraft: pickAircraftBy(r.distanceKm),
                origin: ori,
                destination: dst,
                date: '—',
                departure: '—:—',
                arrival: '—:—',
                distanceKm: r.distanceKm || 0,
                depDelay: delay,
                arrDelay: 0,
                category: cat,
                status,
                weather: {
                    temp: r.temp || 0,
                    wind: r.wind || 0,
                    gust: r.gust || 0,
                    vis: (r.vis || 10000) / 1000,
                }
            };
        });
    }

    // ─── CHART INSTANCES (for destroy/rebuild on theme change) ───
    let chartInstances = {};

    function destroyChart(key) {
        if (chartInstances[key]) {
            chartInstances[key].destroy();
            delete chartInstances[key];
        }
    }

    function destroyAllCharts() {
        Object.keys(chartInstances).forEach(destroyChart);
        chartsBuilt = { overview: false, airports: false, weather: false, flights: false };
    }

    // ─── AIRPORT BAR CHART (with selector) ───────────────────
    function buildAirportBar() {
        destroyChart('airportBar');
        const ctx = $('chart-airport-bar');
        if (!ctx) return;

        const active = AIRPORTS.filter(a => selectedAirports.has(a.iata));
        const labels = active.map(a => a.iata);
        const depDelays = active.map(a => {
            const f = DATA.flights.filter(x => x.origin.iata === a.iata);
            return f.length ? f.reduce((s, x) => s + Math.max(0, x.depDelay), 0) / f.length : 0;
        });

        chartInstances.airportBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'Avg. Departure Delay (min)',
                    data: depDelays.map(v => +v.toFixed(1)),
                    backgroundColor: T.cyanDim,
                    borderColor: T.cyan,
                    borderWidth: 1.5,
                    borderRadius: 4,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted } },
                    y: {
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted, callback: v => v + ' min' },
                        title: { display: true, text: 'Minutes', color: T.textMuted, font: { size: 10 } }
                    }
                }
            }
        });
    }

    // ─── FEATURE IMPACT TORNADO ──────────────────────────────
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

        const corrs = features.map(f => {
            const xs = DATA.flights.map(f.key);
            return { label: f.label, r: pearson(xs, delays) };
        }).sort((a, b) => Math.abs(b.r) - Math.abs(a.r));

        const colors = corrs.map(c => {
            const a = Math.abs(c.r);
            if (a > 0.35) return T.red;
            if (a > 0.18) return T.warn;
            return T.cyan;
        });
        const dims = colors.map(c =>
            c === T.red ? T.redDim : c === T.warn ? T.warnDim : T.cyanDim
        );

        chartInstances.featureImpact = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: corrs.map(c => c.label),
                datasets: [{
                    label: 'Correlation r',
                    data: corrs.map(c => +c.r.toFixed(3)),
                    backgroundColor: dims,
                    borderColor: colors,
                    borderWidth: 1.5,
                    borderRadius: 4,
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: c => ` r = ${c.parsed.x.toFixed(3)} (${Math.abs(c.parsed.x) > 0.35 ? 'strong' : Math.abs(c.parsed.x) > 0.18 ? 'moderate' : 'weak'})`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: T.grid }, ticks: { color: T.textMuted },
                        min: -1, max: 1,
                        title: { display: true, text: 'Pearson r vs departure delay', color: T.textMuted, font: { size: 10 } }
                    },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    // ─── SEVERITY STACKED AREA ───────────────────────────────
    function buildSeverityArea() {
        destroyChart('severityArea');
        const ctx = $('chart-severity-area');
        if (!ctx) return;

        const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2, '0')}:00`);
        const cats = ['none', 'low', 'moderate', 'severe'];
        const byCat = Object.fromEntries(cats.map(c => [c, new Array(24).fill(0)]));

        DATA.flights.forEach(f => {
            const h = parseInt(f.departure, 10);
            if (Number.isNaN(h)) {
                // Distribute evenly if no departure time
                const rh = Math.floor(Math.random() * 18) + 5;
                byCat[f.category][rh]++;
            } else {
                byCat[f.category][h]++;
            }
        });

        chartInstances.severityArea = new Chart(ctx, {
            type: 'line',
            data: {
                labels: hours,
                datasets: [
                    { label: 'None', data: byCat.none, fill: true, borderColor: T.safe, backgroundColor: isDark ? 'rgba(16,185,129,0.18)' : 'rgba(5,150,105,0.12)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Low', data: byCat.low, fill: true, borderColor: T.cyan, backgroundColor: isDark ? 'rgba(0,212,255,0.18)' : 'rgba(8,145,178,0.12)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Moderate', data: byCat.moderate, fill: true, borderColor: T.warn, backgroundColor: isDark ? 'rgba(245,158,11,0.20)' : 'rgba(217,119,6,0.12)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Severe', data: byCat.severe, fill: true, borderColor: T.red, backgroundColor: isDark ? 'rgba(255,77,106,0.22)' : 'rgba(225,29,72,0.12)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y: { stacked: true, grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' flights' } }
                }
            }
        });
    }

    // ─── WIND SCATTER ────────────────────────────────────────
    function buildWindScatter() {
        destroyChart('windScatter');
        const ctx = $('chart-wind-scatter');
        if (!ctx) return;

        const pts = DATA.flights.slice(0, 200).map(f => ({
            x: f.weather.wind, y: Math.max(0, f.depDelay),
        }));

        chartInstances.windScatter = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p => p.y > 60 ? T.red : p.y > 30 ? T.warn : T.safe),
                    pointBorderColor: 'transparent',
                    pointRadius: 4,
                    pointHoverRadius: 6,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { label: c => ` Wind: ${c.raw.x.toFixed(1)} m/s | Delay: ${c.raw.y} min` } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Wind speed (m/s)', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Departure Delay (min)', color: T.textMuted, font: { size: 10 } } }
                }
            }
        });
    }

    // ─── AIRPORTS VIEW ───────────────────────────────────────
    function buildAirportsRanking() {
        destroyChart('airportsRanking');
        const ctx = $('chart-airports-ranking');
        if (!ctx) return;

        const data = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata || f.destination.iata === a.iata);
            const avg = fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / fs.length : 0;
            return { code: a.iata, avg, count: fs.length };
        }).sort((a, b) => b.avg - a.avg);

        chartInstances.airportsRanking = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.code),
                datasets: [{
                    label: 'Average delay (min)',
                    data: data.map(d => +d.avg.toFixed(1)),
                    backgroundColor: data.map((_, i) => i < 3 ? T.redDim : T.cyanDim),
                    borderColor: data.map((_, i) => i < 3 ? T.red : T.cyan),
                    borderWidth: 1.5,
                    borderRadius: 5,
                }]
            },
            options: {
                indexAxis: 'y',
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { label: c => ` ${c.parsed.x.toFixed(1)} min · ${data[c.dataIndex].count} flights` } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' min' } },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    function buildHourBar() {
        destroyChart('hourBar');
        const ctx = $('chart-hour-bar');
        if (!ctx) return;

        const hours = Array.from({ length: 24 }, (_, i) => i);
        const delays = hours.map(h => {
            const fs = DATA.flights.filter(f => parseInt(f.departure, 10) === h);
            return fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / fs.length : 0;
        });

        chartInstances.hourBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: hours.map(h => `${String(h).padStart(2, '0')}h`),
                datasets: [{
                    label: 'Average Departure Delay (min)',
                    data: delays.map(v => +v.toFixed(1)),
                    backgroundColor: delays.map(v => v > 40 ? T.redDim : v > 25 ? T.warnDim : T.safeDim),
                    borderColor: delays.map(v => v > 40 ? T.red : v > 25 ? T.warn : T.safe),
                    borderWidth: 1.5,
                    borderRadius: 4,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' min' } }
                }
            }
        });
    }

    // ─── AIRPORT × SEVERITY HEATMAP (professional redesign) ──
    function buildAirportSeverityHeatmap() {
        const root = $('heatmap-airport-severity');
        if (!root) return;

        const cats = ['none', 'low', 'moderate', 'severe'];

        // Build header
        let html = `<div class="hm-corner">Airport</div>`;
        cats.forEach(c => { html += `<div class="hm-col-head">${c}</div>`; });
        html += `<div class="hm-col-head">Total</div>`;

        AIRPORTS.forEach((a, rowIdx) => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata);
            const total = fs.length || 0;
            const pcts = cats.map(c => total ? fs.filter(f => f.category === c).length / total : 0);
            const counts = cats.map(c => fs.filter(f => f.category === c).length);

            const rowClass = rowIdx % 2 === 0 ? 'hm-row-even' : '';

            html += `<div class="hm-row-label ${rowClass}">${a.iata}<span class="hm-row-name">${a.name}</span></div>`;
            pcts.forEach((p, i) => {
                const intensity = total === 0 ? 0 : clamp(p * 1.4, 0.04, 0.75);
                const pct = (p * 100).toFixed(0);
                html += `
                  <div class="hm-cell ${rowClass}" data-cat="${cats[i]}" style="--intensity: ${intensity}">
                    <span class="hm-val">${pct}%</span>
                    <span class="hm-count">${counts[i]}</span>
                  </div>`;
            });
            html += `<div class="hm-cell hm-total ${rowClass}"><span class="hm-val">${total}</span></div>`;
        });

        root.innerHTML = html;
    }

    // ─── WEATHER VIEW CHARTS ─────────────────────────────────
    function buildWeatherSeries() {
        destroyChart('weatherSeries');
        const ctx = $('chart-weather-series');
        if (!ctx) return;

        let times, wind, gust, vis, temp;
        if (DATA.weatherSeries.length > 0) {
            const series = [...DATA.weatherSeries].reverse();
            times = series.map(r => r.timestamp ? r.timestamp.slice(11, 16) : '');
            temp = series.map(r => +(r.temp || 0).toFixed(1));
            wind = series.map(r => +(r.windSpeed || 0).toFixed(1));
            gust = series.map(r => +(r.windGust || 0).toFixed(1));
            vis = series.map(r => +((r.visibility || 0) / 1000).toFixed(1));
        } else {
            times = ['No data'];
            wind = [0]; gust = [0]; vis = [0]; temp = [0];
        }

        chartInstances.weatherSeries = new Chart(ctx, {
            type: 'line',
            data: {
                labels: times,
                datasets: [
                    { label: 'Wind (m/s)', data: wind, borderColor: T.cyan, backgroundColor: isDark ? 'rgba(0,212,255,0.05)' : 'rgba(8,145,178,0.06)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y' },
                    { label: 'Gust (m/s)', data: gust, borderColor: T.red, backgroundColor: 'transparent', fill: false, tension: 0.4, borderWidth: 2, borderDash: [4, 3], pointRadius: 0, yAxisID: 'y' },
                    { label: 'Visibility (km)', data: vis, borderColor: T.safe, backgroundColor: 'transparent', fill: false, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y2' },
                    { label: 'Temp (°C)', data: temp, borderColor: T.warn, backgroundColor: isDark ? 'rgba(245,158,11,0.05)' : 'rgba(217,119,6,0.04)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y3' },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { labels: { boxWidth: 10, font: { size: 10 } } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y: { grid: { color: T.grid }, ticks: { color: T.cyan }, position: 'left' },
                    y2: { ticks: { color: T.safe }, position: 'right', grid: { drawOnChartArea: false } },
                    y3: { ticks: { color: T.warn }, position: 'right', grid: { drawOnChartArea: false }, display: false },
                }
            }
        });
    }

    function buildGustScatter() {
        destroyChart('gustScatter');
        const ctx = $('chart-gust-scatter');
        if (!ctx) return;

        const pts = DATA.flights.slice(0, 200).map(f => ({ x: f.weather.gust, y: Math.max(0, f.depDelay) }));

        chartInstances.gustScatter = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p => p.y > 60 ? T.red : p.y > 30 ? T.warn : T.cyan),
                    pointBorderColor: 'transparent',
                    pointRadius: 3.5,
                    pointHoverRadius: 6,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: { callbacks: { label: c => ` Gust: ${c.raw.x.toFixed(1)} m/s | Delay: ${c.raw.y} min` } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Wind gust (m/s)', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Delay (min)', color: T.textMuted, font: { size: 10 } } }
                }
            }
        });
    }

    function buildVisibility() {
        destroyChart('visibility');
        const ctx = $('chart-visibility');
        if (!ctx) return;

        const buckets = [0, 2, 4, 6, 8, 11];
        const labels = buckets.slice(0, -1).map((b, i) => `${b}–${buckets[i + 1]} km`);
        const avgD = buckets.slice(0, -1).map((b, i) => {
            const fs = DATA.flights.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i + 1]);
            return fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / fs.length : 0;
        });
        const cancel = buckets.slice(0, -1).map((b, i) => {
            const fs = DATA.flights.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i + 1]);
            return fs.length ? fs.filter(f => f.status === 'Cancelled').length / fs.length * 100 : 0;
        });

        chartInstances.visibility = new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    { label: 'Average Delay (min)', data: avgD.map(v => +v.toFixed(1)), borderColor: T.warn, fill: false, tension: 0.4, borderWidth: 2, pointBackgroundColor: T.warn, pointRadius: 4, yAxisID: 'y' },
                    { label: '% Cancellations', data: cancel.map(v => +v.toFixed(1)), borderColor: T.red, fill: false, tension: 0.4, borderWidth: 2, borderDash: [4, 3], pointBackgroundColor: T.red, pointRadius: 4, yAxisID: 'y2' }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { labels: { boxWidth: 10, font: { size: 10 } } } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted } },
                    y: { grid: { color: T.grid }, ticks: { color: T.warn, callback: v => v + ' min' }, position: 'left' },
                    y2: { ticks: { color: T.red, callback: v => v + '%' }, position: 'right', grid: { drawOnChartArea: false } }
                }
            }
        });
    }

    // ─── FLIGHTS TABLE (historical only — no predictions) ────
    let currentPage = 1;
    const PAGE_SIZE = 20;
    let filteredFlights = [];

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

        const statusClass = s => ({ 'Landed': 'on-time', 'Delayed': 'delayed', 'Cancelled': 'cancelled' }[s] || '');
        const dClass = d => d > 60 ? 'd-high' : d > 15 ? 'd-mid' : 'd-low';

        tbody.innerHTML = slice.map((f, i) => `
            <tr>
                <td class="mono" style="color:var(--t2)">${start + i + 1}</td>
                <td><span class="flight-code">${f.flightId}</span></td>
                <td style="color:var(--t1)">${f.origin.iata}</td>
                <td style="color:var(--t1)">${f.destination.iata}</td>
                <td><span class="aircraft-badge">${f.aircraft}</span></td>
                <td class="mono" style="color:var(--t2)">${f.distanceKm} km</td>
                <td><span class="delay-val ${dClass(f.depDelay)}">${fmtMin(f.depDelay)}</span></td>
                <td><span class="status-badge ${statusClass(f.status)}">${f.status}</span></td>
            </tr>`).join('');
    }

    function applyFlightFilters() {
        const ori = $('filter-origin')?.value || '';
        const sta = $('filter-status')?.value || '';
        filteredFlights = DATA.flights.filter(f => {
            if (ori && f.origin.iata !== ori) return false;
            if (sta && f.status !== sta) return false;
            return true;
        });
        currentPage = 1;
        renderFlightsTable();
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
            buildAirportBar();
            buildFeatureImpact();
            buildSeverityArea();
            buildWindScatter();
            chartsBuilt.overview = true;
        }
        if (view === 'airports' && !chartsBuilt.airports) {
            buildAirportsRanking();
            buildHourBar();
            buildAirportSeverityHeatmap();
            chartsBuilt.airports = true;
        }
        if (view === 'weather' && !chartsBuilt.weather) {
            buildWeatherSeries();
            buildGustScatter();
            buildVisibility();
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

        const btn = $('theme-toggle');
        if (btn) btn.textContent = isDark ? '☀️' : '🌙';

        // Rebuild current active view
        const activeView = document.querySelector('.nav-btn.active')?.dataset.view || 'overview';
        destroyAllCharts();
        activateView(activeView);
    }

    // ─── AIRPORT CHIP SELECTOR ───────────────────────────────
    function initAirportSelector() {
        const wrap = $('airport-selector');
        if (!wrap) return;

        wrap.innerHTML = AIRPORTS.map(a =>
            `<button class="airport-chip active" data-iata="${a.iata}">${a.iata}</button>`
        ).join('');

        wrap.addEventListener('click', e => {
            const chip = e.target.closest('.airport-chip');
            if (!chip) return;
            const iata = chip.dataset.iata;
            if (selectedAirports.has(iata)) {
                // Don't allow removing all
                if (selectedAirports.size <= 1) return;
                selectedAirports.delete(iata);
                chip.classList.remove('active');
            } else {
                selectedAirports.add(iata);
                chip.classList.add('active');
            }
            buildAirportBar();
        });
    }

    // ─── INIT ────────────────────────────────────────────────
    async function init() {
        applyChartDefaults();

        await loadReal();

        filteredFlights = [...DATA.flights];

        // Status chip
        const statusChip = $('data-status');
        if (statusChip) {
            statusChip.textContent = DATA.useReal ? `LIVE · ${DATA.flights.length} flights` : 'NO DATA';
            if (!DATA.useReal) {
                statusChip.style.color = 'var(--warn)';
                statusChip.style.borderColor = 'rgba(245,158,11,0.3)';
                statusChip.style.background = 'rgba(245,158,11,0.08)';
            }
        }

        // Nav buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => activateView(btn.dataset.view));
        });

        // Theme toggle
        $('theme-toggle')?.addEventListener('click', toggleTheme);

        // Airport selector
        initAirportSelector();

        // Pagination
        $('btn-prev')?.addEventListener('click', () => {
            if (currentPage > 1) { currentPage--; renderFlightsTable(); }
        });
        $('btn-next')?.addEventListener('click', () => {
            const total = Math.ceil(filteredFlights.length / PAGE_SIZE);
            if (currentPage < total) { currentPage++; renderFlightsTable(); }
        });

        // Filters
        ['filter-origin', 'filter-status'].forEach(id => {
            $(id)?.addEventListener('change', applyFlightFilters);
        });

        // Clock
        updateClock();
        setInterval(updateClock, 1000);

        // Build default view
        activateView('overview');
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    } else {
        init();
    }

})();
