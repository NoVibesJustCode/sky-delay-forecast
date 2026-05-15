/* ============================================
   SKYDELAY — app.js
   Flight Delay Intelligence Dashboard
   Historical data only — powered by flight_features datamart
   ============================================ */

(() => {
    'use strict';

    // ─── THEME (light default, dark as option) ───────────────
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

    // ─── AIRPORTS ────────────────────────────────────────────
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
    let DATA = { flights: [], weatherCache: {}, useReal: false };

    // ─── FETCH REAL DATA ─────────────────────────────────────
    async function loadReal() {
        try {
            const featRes = await fetch('/api/flight-features');
            if (featRes.ok) {
                const features = await featRes.json();
                if (Array.isArray(features) && features.length > 0) {
                    DATA.flights = enrichFromFeatures(features);
                    DATA.useReal = true;
                }
            }
        } catch (e) {
            console.warn('API not available:', e.message);
        }
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
        } catch (e) { console.warn('Weather API error:', e.message); }
        return [];
    }

    function enrichFromFeatures(rows) {
        return rows.map((r, i) => {
            const ori = AIRPORTS.find(a => a.icao === r.originIcao) || { iata: r.originIcao || '???', icao: r.originIcao || '????', name: r.originIcao || '???' };
            const dst = AIRPORTS.find(a => a.icao === r.destIcao)   || { iata: r.destIcao   || '???', icao: r.destIcao   || '????', name: r.destIcao   || '???' };
            const cat = r.delayCategory || delayCategory(r.departureDelay);
            const delay = r.departureDelay || 0;
            let status;
            if (delay <= 15) status = 'On Time';
            else if (delay > 120) status = 'Cancelled';
            else status = 'Delayed';

            // Parse scheduled departure for hour/date extraction
            let hour = null, dateStr = null;
            if (r.scheduledDeparture) {
                const d = new Date(r.scheduledDeparture);
                if (!isNaN(d.getTime())) {
                    hour = d.getUTCHours();
                    dateStr = r.scheduledDeparture.slice(0, 10); // YYYY-MM-DD
                }
            }

            return {
                id: i + 1,
                flightId: r.flightId || '—',
                airline: r.flightId?.split(' ')[0] || 'Unknown',
                aircraft: r.aircraftModel || pickAircraftBy(r.distanceKm),
                origin: ori,
                destination: dst,
                distanceKm: r.distanceKm || 0,
                depDelay: delay,
                category: cat,
                status,
                hour,
                dateStr,
                weather: {
                    temp: r.temp || 0,
                    wind: r.wind || 0,
                    gust: r.gust || 0,
                    vis: (r.vis || 10000) / 1000,
                }
            };
        });
    }

    // ─── CHART INSTANCES ─────────────────────────────────────
    let chartInstances = {};
    function destroyChart(key) { if (chartInstances[key]) { chartInstances[key].destroy(); delete chartInstances[key]; } }
    function destroyAllCharts() { Object.keys(chartInstances).forEach(destroyChart); chartsBuilt = { overview: false, airports: false, weather: false, flights: false }; }

    // ================================================================
    //  OVERVIEW TAB
    // ================================================================

    // ① OTP Bar — On-time performance per airport
    function buildOTPBar() {
        destroyChart('otpBar');
        const ctx = $('chart-otp-bar');
        if (!ctx) return;

        const data = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata);
            const onTime = fs.filter(f => f.depDelay <= 15).length;
            const otp = fs.length ? (onTime / fs.length) * 100 : 0;
            return { iata: a.iata, name: a.name, otp, count: fs.length };
        }).sort((a, b) => a.otp - b.otp); // worst first

        const colors = data.map(d => d.otp >= 80 ? T.safe : d.otp >= 60 ? T.warn : T.red);
        const bgColors = data.map(d => d.otp >= 80 ? T.safeDim : d.otp >= 60 ? T.warnDim : T.redDim);

        chartInstances.otpBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.iata),
                datasets: [{
                    label: 'On-Time %',
                    data: data.map(d => +d.otp.toFixed(1)),
                    backgroundColor: bgColors,
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
                            label: c => {
                                const d = data[c.dataIndex];
                                return ` OTP: ${d.otp.toFixed(1)}% · ${d.count} flights`;
                            }
                        }
                    }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + '%' }, min: 0, max: 100 },
                    y: { grid: { color: T.grid }, ticks: { color: T.text, font: { weight: '700' } } }
                }
            }
        });
    }

    // ② Feature Impact Tornado
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
        const dims = colors.map(c => c === T.red ? T.redDim : c === T.warn ? T.warnDim : T.cyanDim);

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
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, min: -1, max: 1,
                        title: { display: true, text: 'Pearson r vs departure delay', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    // ③ Severity Trend — stacked bar by date with airport filter
    function buildSeverityTrend() {
        destroyChart('severityTrend');
        const ctx = $('chart-severity-trend');
        if (!ctx) return;

        const filterIcao = $('severity-airport-filter')?.value || '';
        let flights = DATA.flights;
        if (filterIcao) flights = flights.filter(f => f.origin.icao === filterIcao);

        // Group by date
        const byDate = {};
        flights.forEach(f => {
            const key = f.dateStr || 'Unknown';
            if (!byDate[key]) byDate[key] = { none: 0, low: 0, moderate: 0, severe: 0 };
            byDate[key][f.category]++;
        });

        const dates = Object.keys(byDate).sort();
        const cats = ['none', 'low', 'moderate', 'severe'];
        const catColors = {
            none:     { bg: isDark ? 'rgba(16,185,129,0.55)' : 'rgba(5,150,105,0.50)',  border: T.safe },
            low:      { bg: isDark ? 'rgba(0,212,255,0.55)'  : 'rgba(8,145,178,0.50)',  border: T.cyan },
            moderate: { bg: isDark ? 'rgba(245,158,11,0.60)' : 'rgba(217,119,6,0.55)',  border: T.warn },
            severe:   { bg: isDark ? 'rgba(255,77,106,0.65)' : 'rgba(225,29,72,0.55)',  border: T.red },
        };

        chartInstances.severityTrend = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: dates.map(d => d === 'Unknown' ? 'N/A' : d.slice(5)),
                datasets: cats.map(c => ({
                    label: c.charAt(0).toUpperCase() + c.slice(1),
                    data: dates.map(d => byDate[d][c]),
                    backgroundColor: catColors[c].bg,
                    borderColor: catColors[c].border,
                    borderWidth: 1,
                    borderRadius: 2,
                }))
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { display: false } },
                scales: {
                    x: { stacked: true, grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 14 } },
                    y: { stacked: true, grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' flights' } }
                }
            }
        });
    }

    // ④ Aircraft Model Donut
    function buildAircraftDonut() {
        destroyChart('aircraftDonut');
        const ctx = $('chart-aircraft-donut');
        if (!ctx) return;

        const byModel = {};
        DATA.flights.forEach(f => {
            const m = f.aircraft || 'Unknown';
            if (!byModel[m]) byModel[m] = { count: 0, totalDelay: 0 };
            byModel[m].count++;
            byModel[m].totalDelay += Math.max(0, f.depDelay);
        });

        const models = Object.entries(byModel)
            .map(([name, d]) => ({ name, count: d.count, avg: d.count ? d.totalDelay / d.count : 0 }))
            .sort((a, b) => b.count - a.count);

        const palette = [T.cyan, T.warn, T.red, T.safe, T.violet, '#f472b6', '#60a5fa', '#a78bfa'];
        const dimPalette = palette.map(c => c + '33');

        chartInstances.aircraftDonut = new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: models.map(m => m.name),
                datasets: [{
                    data: models.map(m => m.count),
                    backgroundColor: models.map((_, i) => dimPalette[i % dimPalette.length]),
                    borderColor: models.map((_, i) => palette[i % palette.length]),
                    borderWidth: 2,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '60%',
                plugins: {
                    legend: { position: 'right', labels: { boxWidth: 12, font: { size: 10 }, padding: 8 } },
                    tooltip: {
                        callbacks: {
                            label: c => {
                                const m = models[c.dataIndex];
                                return ` ${m.count} flights · avg delay ${m.avg.toFixed(1)} min`;
                            }
                        }
                    }
                }
            }
        });
    }

    // ================================================================
    //  AIRPORTS TAB
    // ================================================================

    // ⑤ Airport Performance Bubble
    function buildAirportBubble() {
        destroyChart('airportBubble');
        const ctx = $('chart-airport-bubble');
        if (!ctx) return;

        const data = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata);
            const count = fs.length;
            const onTime = fs.filter(f => f.depDelay <= 15).length;
            const otp = count ? (onTime / count) * 100 : 0;
            const avgDelay = count ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / count : 0;
            return { iata: a.iata, name: a.name, count, otp, avgDelay };
        }).filter(d => d.count > 0);

        const maxDelay = Math.max(...data.map(d => d.avgDelay), 1);

        // Custom plugin to draw IATA labels on bubbles
        const labelPlugin = {
            id: 'bubbleLabels',
            afterDraw(chart) {
                const ctx2 = chart.ctx;
                ctx2.save();
                ctx2.font = "bold 10px 'Fira Code', monospace";
                ctx2.textAlign = 'center';
                ctx2.textBaseline = 'middle';
                ctx2.fillStyle = T.text;
                chart.getDatasetMeta(0).data.forEach((pt, i) => {
                    ctx2.fillText(data[i].iata, pt.x, pt.y);
                });
                ctx2.restore();
            }
        };

        chartInstances.airportBubble = new Chart(ctx, {
            type: 'bubble',
            data: {
                datasets: [{
                    label: 'Airports',
                    data: data.map(d => ({
                        x: d.count,
                        y: d.otp,
                        r: clamp(5 + (d.avgDelay / maxDelay) * 22, 6, 30),
                    })),
                    backgroundColor: data.map(d => d.otp >= 80 ? T.safeDim : d.otp >= 60 ? T.warnDim : T.redDim),
                    borderColor: data.map(d => d.otp >= 80 ? T.safe : d.otp >= 60 ? T.warn : T.red),
                    borderWidth: 2,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: c => {
                                const d = data[c.dataIndex];
                                return [
                                    ` ${d.name}`,
                                    ` OTP: ${d.otp.toFixed(1)}%`,
                                    ` Flights: ${d.count}`,
                                    ` Avg delay: ${d.avgDelay.toFixed(1)} min`
                                ];
                            }
                        }
                    }
                },
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

    // ⑥ Delays by Hour of Day (fixed — uses real scheduled_departure)
    function buildHourBar() {
        destroyChart('hourBar');
        const ctx = $('chart-hour-bar');
        if (!ctx) return;

        const withHour = DATA.flights.filter(f => f.hour !== null);
        const hours = Array.from({ length: 24 }, (_, i) => i);
        const delays = hours.map(h => {
            const fs = withHour.filter(f => f.hour === h);
            return fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay), 0) / fs.length : 0;
        });
        const counts = hours.map(h => withHour.filter(f => f.hour === h).length);

        chartInstances.hourBar = new Chart(ctx, {
            type: 'bar',
            data: {
                labels: hours.map(h => `${String(h).padStart(2, '0')}h`),
                datasets: [{
                    label: 'Avg Departure Delay',
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
                plugins: {
                    legend: { display: false },
                    tooltip: {
                        callbacks: {
                            label: c => {
                                const h = c.dataIndex;
                                return ` ${c.parsed.y} min avg · ${counts[h]} flights`;
                            }
                        }
                    }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' min' } }
                }
            }
        });
    }

    // ⑦ Airport × Severity Heatmap (improved)
    function buildAirportSeverityHeatmap() {
        const root = $('heatmap-airport-severity');
        if (!root) return;

        const cats = ['none', 'low', 'moderate', 'severe'];
        const catLabels = { none: 'On Time', low: 'Low', moderate: 'Moderate', severe: 'Severe' };
        const catBarColors = { none: 'var(--safe)', low: 'var(--cyan)', moderate: 'var(--warn)', severe: 'var(--red)' };

        let html = `<div class="hm-corner"></div>`;
        cats.forEach(c => { html += `<div class="hm-col-head">${catLabels[c]}</div>`; });
        html += `<div class="hm-col-head">Flights</div>`;

        // Sort airports by total delayed flights (most problematic first)
        const airportData = AIRPORTS.map(a => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata);
            return { ...a, flights: fs, total: fs.length };
        }).sort((a, b) => b.total - a.total);

        airportData.forEach((a, rowIdx) => {
            const total = a.total || 0;
            const pcts = cats.map(c => total ? a.flights.filter(f => f.category === c).length / total : 0);
            const counts = cats.map(c => a.flights.filter(f => f.category === c).length);
            const rowClass = rowIdx % 2 === 0 ? 'hm-row-even' : '';

            html += `<div class="hm-row-label ${rowClass}">
                <span class="hm-iata">${a.iata}</span>
                <span class="hm-row-name">${a.name}</span>
            </div>`;

            pcts.forEach((p, i) => {
                const pct = (p * 100).toFixed(0);
                const barW = Math.max(2, p * 100);
                html += `
                  <div class="hm-cell ${rowClass}" data-cat="${cats[i]}">
                    <div class="hm-bar-track">
                      <div class="hm-bar-fill" style="width:${barW}%; background:${catBarColors[cats[i]]}"></div>
                    </div>
                    <span class="hm-val">${pct}%</span>
                    <span class="hm-count">(${counts[i]})</span>
                  </div>`;
            });
            html += `<div class="hm-cell hm-total ${rowClass}"><span class="hm-val">${total}</span></div>`;
        });

        root.innerHTML = html;
    }

    // ================================================================
    //  WEATHER TAB
    // ================================================================

    // ⑧ Weather Series with airport selector
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
        } else {
            times = ['No data']; wind = [0]; gust = [0]; vis = [0]; temp = [0];
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
                    y:  { grid: { color: T.grid }, ticks: { color: T.cyan }, position: 'left' },
                    y2: { ticks: { color: T.safe }, position: 'right', grid: { drawOnChartArea: false } },
                    y3: { ticks: { color: T.warn }, position: 'right', grid: { drawOnChartArea: false }, display: false },
                }
            }
        });
    }

    // ⑨ Unified Weather Scatter with variable picker
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
        const pts = DATA.flights.map(f => ({ x: v.key(f), y: Math.max(0, f.depDelay) }));

        chartInstances.weatherScatter = new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p => p.y > 60 ? T.red : p.y > 30 ? T.warn : p.y > 15 ? T.cyan : T.safe),
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
                    tooltip: { callbacks: { label: c => ` ${v.label.split(' (')[0]}: ${c.raw.x.toFixed(1)} | Delay: ${c.raw.y} min` } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted },
                        title: { display: true, text: v.label, color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted },
                        title: { display: true, text: 'Departure Delay (min)', color: T.textMuted, font: { size: 10 } } }
                }
            }
        });
    }

    // ⑩ Visibility Sankey / Alluvial Flow
    function buildVisibilitySankey() {
        const container = $('sankey-visibility');
        if (!container) return;

        const VIS_RANGES = [
            { label: '0–2 km', min: 0, max: 2 },
            { label: '2–4 km', min: 2, max: 4 },
            { label: '4–6 km', min: 4, max: 6 },
            { label: '6–8 km', min: 6, max: 8 },
            { label: '8+ km',  min: 8, max: Infinity },
        ];
        const CATS = ['none', 'low', 'moderate', 'severe'];
        const CAT_LABELS = { none: 'On Time', low: 'Low Delay', moderate: 'Moderate', severe: 'Severe' };
        const CAT_CSS = { none: 'var(--safe)', low: 'var(--cyan)', moderate: 'var(--warn)', severe: 'var(--red)' };

        // Build flows matrix
        const matrix = VIS_RANGES.map(vr => {
            const fs = DATA.flights.filter(f => f.weather.vis >= vr.min && f.weather.vis < vr.max);
            const counts = {};
            CATS.forEach(c => counts[c] = fs.filter(f => f.category === c).length);
            return { ...vr, total: fs.length, counts };
        });

        const totalFlights = Math.max(1, matrix.reduce((s, m) => s + m.total, 0));
        const catTotals = {};
        CATS.forEach(c => { catTotals[c] = matrix.reduce((s, m) => s + m.counts[c], 0); });

        // SVG dimensions
        const W = container.clientWidth || 680;
        const H = 320;
        const PAD = 40;
        const NODE_W = 20;
        const LABEL_W = 80;
        const leftX = LABEL_W + PAD;
        const rightX = W - LABEL_W - PAD;
        const gap = 6;
        const usableH = H - 2 * PAD - (Math.max(VIS_RANGES.length, CATS.length) - 1) * gap;

        // Compute node Y positions
        function nodePositions(items, totalKey) {
            let y = PAD;
            return items.map(item => {
                const h = Math.max(6, (item[totalKey] / totalFlights) * usableH);
                const pos = { y, h };
                y += h + gap;
                return pos;
            });
        }

        const leftNodes = nodePositions(matrix, 'total');
        const rightItems = CATS.map(c => ({ total: catTotals[c] }));
        const rightNodes = nodePositions(rightItems, 'total');

        // Track cumulative offsets within each node for stacking
        const leftOffsets = matrix.map(() => 0);
        const rightOffsets = CATS.map(() => 0);

        const catSVGColors = {
            none: isDark ? 'rgba(16,185,129,0.35)' : 'rgba(5,150,105,0.30)',
            low: isDark ? 'rgba(0,212,255,0.35)' : 'rgba(8,145,178,0.30)',
            moderate: isDark ? 'rgba(245,158,11,0.40)' : 'rgba(217,119,6,0.30)',
            severe: isDark ? 'rgba(255,77,106,0.45)' : 'rgba(225,29,72,0.30)'
        };
        const catStroke = {
            none: isDark ? 'rgba(16,185,129,0.6)' : 'rgba(5,150,105,0.5)',
            low: isDark ? 'rgba(0,212,255,0.6)' : 'rgba(8,145,178,0.5)',
            moderate: isDark ? 'rgba(245,158,11,0.65)' : 'rgba(217,119,6,0.5)',
            severe: isDark ? 'rgba(255,77,106,0.7)' : 'rgba(225,29,72,0.5)'
        };

        let paths = '';
        matrix.forEach((vr, vi) => {
            CATS.forEach((cat, ci) => {
                const count = vr.counts[cat];
                if (count === 0) return;

                const lNode = leftNodes[vi];
                const rNode = rightNodes[ci];
                const lH = (count / Math.max(1, vr.total)) * lNode.h;
                const rH = (count / Math.max(1, catTotals[cat])) * rNode.h;

                const y0 = lNode.y + leftOffsets[vi];
                const y1 = rNode.y + rightOffsets[ci];
                leftOffsets[vi] += lH;
                rightOffsets[ci] += rH;

                const mx = (leftX + NODE_W + rightX) / 2;
                const x0 = leftX + NODE_W;
                const x1 = rightX;

                paths += `<path d="M${x0},${y0} C${mx},${y0} ${mx},${y1} ${x1},${y1} L${x1},${y1 + rH} C${mx},${y1 + rH} ${mx},${y0 + lH} ${x0},${y0 + lH} Z"
                    fill="${catSVGColors[cat]}" stroke="${catStroke[cat]}" stroke-width="0.5" opacity="0.85">
                    <title>${vr.label} → ${CAT_LABELS[cat]}: ${count} flights</title></path>`;
            });
        });

        // Left nodes
        let leftNodesSVG = '';
        matrix.forEach((vr, i) => {
            const n = leftNodes[i];
            leftNodesSVG += `<rect x="${leftX}" y="${n.y}" width="${NODE_W}" height="${n.h}" rx="3" fill="${T.textMuted}" opacity="0.3"/>`;
            leftNodesSVG += `<text x="${leftX - 8}" y="${n.y + n.h / 2}" text-anchor="end" dominant-baseline="middle"
                font-size="10" font-family="'Fira Code', monospace" fill="${T.textMuted}">${vr.label}</text>`;
        });

        // Right nodes
        let rightNodesSVG = '';
        CATS.forEach((cat, i) => {
            const n = rightNodes[i];
            rightNodesSVG += `<rect x="${rightX}" y="${n.y}" width="${NODE_W}" height="${n.h}" rx="3" fill="${catStroke[cat]}" opacity="0.5"/>`;
            rightNodesSVG += `<text x="${rightX + NODE_W + 8}" y="${n.y + n.h / 2}" text-anchor="start" dominant-baseline="middle"
                font-size="10" font-family="'Fira Code', monospace" font-weight="600" fill="${T.textMuted}">${CAT_LABELS[cat]}</text>`;
            rightNodesSVG += `<text x="${rightX + NODE_W + 8}" y="${n.y + n.h / 2 + 13}" text-anchor="start" dominant-baseline="middle"
                font-size="9" font-family="'Fira Code', monospace" fill="${T.textMuted}" opacity="0.6">${catTotals[cat]} flights</text>`;
        });

        container.innerHTML = `<svg width="${W}" height="${H}" xmlns="http://www.w3.org/2000/svg">
            ${paths}${leftNodesSVG}${rightNodesSVG}
        </svg>`;
    }

    // ================================================================
    //  FLIGHTS TAB
    // ================================================================

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

        const statusClass = s => ({ 'On Time': 'on-time', 'Delayed': 'delayed', 'Cancelled': 'cancelled' }[s] || '');
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
            buildOTPBar();
            buildFeatureImpact();
            buildSeverityTrend();
            buildAircraftDonut();
            chartsBuilt.overview = true;
        }
        if (view === 'airports' && !chartsBuilt.airports) {
            buildAirportBubble();
            buildHourBar();
            buildAirportSeverityHeatmap();
            chartsBuilt.airports = true;
        }
        if (view === 'weather' && !chartsBuilt.weather) {
            buildWeatherSeries();
            buildWeatherScatter();
            buildVisibilitySankey();
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
        const activeView = document.querySelector('.nav-btn.active')?.dataset.view || 'overview';
        destroyAllCharts();
        activateView(activeView);
    }

    // ─── INIT ────────────────────────────────────────────────
    async function init() {
        // Apply light mode by default
        document.body.classList.add('light-mode');
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

        // Nav
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => activateView(btn.dataset.view));
        });

        // Theme toggle
        $('theme-toggle')?.addEventListener('click', toggleTheme);

        // Severity airport filter
        $('severity-airport-filter')?.addEventListener('change', () => {
            buildSeverityTrend();
        });

        // Weather airport selector
        $('weather-airport-select')?.addEventListener('change', async (e) => {
            currentWeatherIcao = e.target.value;
            await buildWeatherSeries();
        });

        // Weather variable picker
        $('weather-var-select')?.addEventListener('change', (e) => {
            currentWeatherVar = e.target.value;
            buildWeatherScatter();
        });

        // Pagination
        $('btn-prev')?.addEventListener('click', () => { if (currentPage > 1) { currentPage--; renderFlightsTable(); } });
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
