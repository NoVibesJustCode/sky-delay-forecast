/* ============================================
   SKYDELAY — app.js
   Flight Delay Intelligence Dashboard
   ============================================ */

(() => {
    'use strict';

    // ─── THEME ───────────────────────────────────────────────
    const T = {
        cyan:      '#00d4ff',
        cyanDim:   'rgba(0,212,255,0.12)',
        cyanMid:   'rgba(0,212,255,0.25)',
        red:       '#ff4d6a',
        redDim:    'rgba(255,77,106,0.12)',
        warn:      '#f59e0b',
        warnDim:   'rgba(245,158,11,0.12)',
        safe:      '#10b981',
        safeDim:   'rgba(16,185,129,0.12)',
        violet:    '#7c3aed',
        violetDim: 'rgba(124,58,237,0.12)',
        slate:     '#4a6080',
        text:      '#e2e8f8',
        textMuted: '#4a6080',
        surface:   'rgba(6,13,26,0.92)',
        grid:      'rgba(0,212,255,0.05)',
        border:    'rgba(0,212,255,0.07)',
    };

    Chart.defaults.color = T.textMuted;
    Chart.defaults.font.family = "'Fira Code', monospace";
    Chart.defaults.font.size = 11;
    Chart.defaults.plugins.tooltip.backgroundColor = '#040c1a';
    Chart.defaults.plugins.tooltip.borderColor = 'rgba(0,212,255,0.2)';
    Chart.defaults.plugins.tooltip.borderWidth = 1;
    Chart.defaults.plugins.tooltip.padding = 10;
    Chart.defaults.plugins.tooltip.titleFont = { family: "'Space Grotesk', sans-serif", size: 13, weight: '700' };
    Chart.defaults.plugins.tooltip.bodyFont = { family: "'Fira Code', monospace", size: 11 };
    Chart.defaults.plugins.legend.labels.color = T.textMuted;
    Chart.defaults.plugins.legend.labels.padding = 16;

    // ─── HELPERS ─────────────────────────────────────────────
    const $ = id => document.getElementById(id);
    const rnd = (a, b) => Math.random() * (b - a) + a;
    const rndInt = (a, b) => Math.round(rnd(a, b));
    const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v));
    const fmt0 = n => n.toFixed(0);
    const fmtMin = m => m <= 0 ? '0 min' : `+${Math.round(m)} min`;
    const fmtPct = p => (p * 100).toFixed(1) + '%';

    // ─── AIRPORTS DATA ────────────────────────────────────────
    const AIRPORTS = [
        { code: 'LEMD', name: 'Madrid Barajas', city: 'Madrid' },
        { code: 'LEBL', name: 'Barcelona El Prat', city: 'Barcelona' },
        { code: 'LEMG', name: 'Málaga Costa del Sol', city: 'Málaga' },
        { code: 'GCLP', name: 'Gran Canaria', city: 'Las Palmas' },
        { code: 'LEPA', name: 'Palma de Mallorca', city: 'Palma' },
        { code: 'LEAL', name: 'Alicante-Elche', city: 'Alicante' },
        { code: 'LEVC', name: 'Valencia', city: 'Valencia' },
        { code: 'LEBB', name: 'Bilbao', city: 'Bilbao' },
        { code: 'LEZL', name: 'Sevilla', city: 'Sevilla' },
        { code: 'GCXO', name: 'Tenerife Norte', city: 'Tenerife' },
    ];

    const AIRCRAFT = ['Boeing 737', 'Airbus A320', 'Airbus A321', 'Boeing 787', 'Airbus A319', 'Embraer 190'];
    const AIRLINES = ['Iberia', 'Vueling', 'Ryanair', 'Air Europa', 'EasyJet', 'Norwegian'];

    // ─── GENERATE MOCK FLIGHTS ───────────────────────────────
    function genFlights(n = 300) {
        const flights = [];
        for (let i = 0; i < n; i++) {
            const ori = AIRPORTS[rndInt(0, AIRPORTS.length - 1)];
            let dst;
            do { dst = AIRPORTS[rndInt(0, AIRPORTS.length - 1)]; } while (dst.code === ori.code);

            const airline = AIRLINES[rndInt(0, AIRLINES.length - 1)];
            const acft = AIRCRAFT[rndInt(0, AIRCRAFT.length - 1)];
            const depH = rndInt(5, 23);
            const depM = rndInt(0, 59);
            const duration = rndInt(60, 240);

            // weather factors for this flight
            const wind = rndInt(5, 60);        // km/h
            const windGust = wind + rndInt(0, 25);
            const vis = rndInt(1, 10);          // km
            const rain = rndInt(0, 20);         // mm/h
            const temp = rndInt(-5, 38);        // °C

            // delay probability from weather
            let delayProb = 0.1;
            if (wind > 40) delayProb += 0.2;
            if (windGust > 55) delayProb += 0.2;
            if (vis < 3) delayProb += 0.25;
            if (rain > 10) delayProb += 0.15;
            delayProb = clamp(delayProb + rnd(-0.05, 0.12), 0.05, 0.97);

            const isDelayed = Math.random() < delayProb;
            const depDelay = isDelayed ? rndInt(5, 180) : rndInt(0, 10);
            const arrDelay = isDelayed ? depDelay + rndInt(-5, 30) : rndInt(0, 8);
            const totalDelay = Math.max(0, depDelay + arrDelay);

            let status = 'on-time';
            if (totalDelay > 60) status = 'critical';
            else if (totalDelay > 15) status = 'delayed';
            if (Math.random() < 0.015) status = 'cancelled';

            const date = new Date();
            date.setHours(depH, depM, 0, 0);

            flights.push({
                id: i + 1,
                code: airline.substring(0, 2).toUpperCase() + rndInt(100, 9999),
                airline, aircraft: acft,
                origin: ori, destination: dst,
                departure: `${String(depH).padStart(2,'0')}:${String(depM).padStart(2,'0')}`,
                arrival: `${String(Math.floor((depH * 60 + depM + duration) / 60) % 24).padStart(2,'0')}:${String((depM + duration) % 60).padStart(2,'0')}`,
                depDelay, arrDelay, totalDelay,
                status, delayProb,
                weather: { wind, windGust, vis, rain, temp }
            });
        }
        return flights;
    }

    const ALL_FLIGHTS = genFlights(300);

    // ─── AIRPORT BAR CHART ───────────────────────────────────
    function buildAirportBar() {
        const ctx = $('chart-airport-bar');
        if (!ctx) return;

        const labels = AIRPORTS.map(a => a.code);
        const depDelays = AIRPORTS.map(a => {
            const flights = ALL_FLIGHTS.filter(f => f.origin.code === a.code);
            return flights.length ? flights.reduce((s, f) => s + f.depDelay, 0) / flights.length : 0;
        });
        const arrDelays = AIRPORTS.map(a => {
            const flights = ALL_FLIGHTS.filter(f => f.destination.code === a.code);
            return flights.length ? flights.reduce((s, f) => s + f.arrDelay, 0) / flights.length : 0;
        });

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Departure Delay (min)',
                        data: depDelays.map(v => +v.toFixed(1)),
                        backgroundColor: T.cyanDim,
                        borderColor: T.cyan,
                        borderWidth: 1.5,
                        borderRadius: 4,
                    },
                    {
                        label: 'Arrival Delay (min)',
                        data: arrDelays.map(v => +v.toFixed(1)),
                        backgroundColor: T.redDim,
                        borderColor: T.red,
                        borderWidth: 1.5,
                        borderRadius: 4,
                    }
                ]
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

    // ─── CAUSES PIE CHART ────────────────────────────────────
    function buildCausesPie() {
        const ctx = $('chart-causes-pie');
        if (!ctx) return;

        new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: ['Weather', 'Operations', 'Air Traffic Control', 'Aircraft', 'Passengers', 'Other'],
                datasets: [{
                    data: [38, 22, 16, 12, 7, 5],
                    backgroundColor: [
                        T.cyanDim, T.redDim, T.warnDim, T.violetDim,
                        T.safeDim, 'rgba(100,100,140,0.12)'
                    ],
                    borderColor: [T.cyan, T.red, T.warn, T.violet, T.safe, '#64648c'],
                    borderWidth: 1.5,
                    hoverOffset: 8,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '62%',
                plugins: {
                    legend: {
                        position: 'bottom',
                        labels: { boxWidth: 10, padding: 12, color: T.textMuted, font: { size: 10 } }
                    },
                    tooltip: {
                        callbacks: { label: ctx => ` ${ctx.label}: ${ctx.parsed}%` }
                    }
                }
            }
        });
    }

    // ─── TREND AREA CHART ────────────────────────────────────
    function buildTrendArea() {
        const ctx = $('chart-trend-area');
        if (!ctx) return;

        const hours = Array.from({ length: 25 }, (_, i) => `${String(i).padStart(2,'0')}:00`);
        const delays = hours.map((_, i) => {
            const base = 15 + Math.sin(i * 0.4) * 8;
            return +(base + rnd(-4, 6)).toFixed(1);
        });
        const probs = hours.map((_, i) => +(30 + Math.sin(i * 0.35 + 1) * 15 + rnd(-5, 8)).toFixed(1));

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: hours,
                datasets: [
                    {
                        label: 'Average Delay (min)',
                        data: delays,
                        fill: true,
                        backgroundColor: 'rgba(0,212,255,0.06)',
                        borderColor: T.cyan,
                        borderWidth: 2,
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        tension: 0.45,
                        yAxisID: 'y',
                    },
                    {
                        label: '% Delayed Flights',
                        data: probs,
                        fill: true,
                        backgroundColor: 'rgba(255,77,106,0.05)',
                        borderColor: T.red,
                        borderWidth: 2,
                        borderDash: [5, 3],
                        pointRadius: 0,
                        pointHoverRadius: 4,
                        tension: 0.45,
                        yAxisID: 'y2',
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: {
                    legend: { labels: { boxWidth: 10, font: { size: 10 } } }
                },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y: {
                        grid: { color: T.grid },
                        ticks: { color: T.cyan, callback: v => v + ' min' },
                        position: 'left',
                    },
                    y2: {
                        ticks: { color: T.red, callback: v => v + '%' },
                        position: 'right',
                        grid: { drawOnChartArea: false },
                    }
                }
            }
        });
    }

    // ─── WIND SCATTER ─────────────────────────────────────────
    function buildWindScatter() {
        const ctx = $('chart-wind-scatter');
        if (!ctx) return;

        const pts = ALL_FLIGHTS.slice(0, 120).map(f => ({
            x: f.weather.wind,
            y: f.totalDelay,
        }));

        new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p =>
                        p.y > 60 ? T.red : p.y > 20 ? T.warn : T.safe
                    ),
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
                    tooltip: {
                        callbacks: {
                            label: c => ` Wind: ${c.raw.x} km/h | Delay: ${c.raw.y} min`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted },
                        title: { display: true, text: 'Wind (km/h)', color: T.textMuted, font: { size: 10 } }
                    },
                    y: {
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted },
                        title: { display: true, text: 'Total Delay (min)', color: T.textMuted, font: { size: 10 } }
                    }
                }
            }
        });
    }

    // ─── WEATHER RADAR CHART ──────────────────────────────────
    function buildWeatherRadar() {
        const ctx = $('chart-weather-radar');
        if (!ctx) return;

        new Chart(ctx, {
            type: 'radar',
            data: {
                labels: ['Wind', 'Visibility\n(inv.)', 'Rain', 'Wind Gust', 'Extreme\ntemperature', 'Fog'],
                datasets: [
                    {
                        label: 'Current Conditions',
                        data: [72, 55, 40, 68, 30, 20],
                        borderColor: T.cyan,
                        backgroundColor: 'rgba(0,212,255,0.08)',
                        borderWidth: 2,
                        pointBackgroundColor: T.cyan,
                        pointRadius: 3,
                    },
                    {
                        label: 'Critical Threshold',
                        data: [80, 80, 80, 80, 80, 80],
                        borderColor: T.red,
                        backgroundColor: 'rgba(255,77,106,0.04)',
                        borderWidth: 1,
                        borderDash: [4, 3],
                        pointRadius: 0,
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                plugins: {
                    legend: { labels: { boxWidth: 10, font: { size: 10 } } }
                },
                scales: {
                    r: {
                        grid: { color: 'rgba(0,212,255,0.08)' },
                        angleLines: { color: 'rgba(0,212,255,0.06)' },
                        ticks: { color: T.textMuted, backdropColor: 'transparent', stepSize: 20 },
                        pointLabels: { color: T.text, font: { size: 10 } },
                        min: 0, max: 100,
                    }
                }
            }
        });
    }

    // ─── AIRPORTS VIEW CHARTS ────────────────────────────────
    function buildAirportsRanking() {
        const ctx = $('chart-airports-ranking');
        if (!ctx) return;

        const data = AIRPORTS.map(a => {
            const fs = ALL_FLIGHTS.filter(f => f.origin.code === a.code || f.destination.code === a.code);
            return { code: a.code, avg: fs.length ? fs.reduce((s,f)=>s+f.totalDelay,0)/fs.length : 0 };
        }).sort((a, b) => b.avg - a.avg);

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.code),
                datasets: [{
                    label: 'Total Average Delay (min)',
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
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v + ' min' } },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    function buildHourBar() {
        const ctx = $('chart-hour-bar');
        if (!ctx) return;

        const hours = Array.from({length:24}, (_,i)=>i);
        const delays = hours.map(h => {
            const fs = ALL_FLIGHTS.filter(f => parseInt(f.departure) === h);
            return fs.length ? fs.reduce((s,f)=>s+f.totalDelay,0)/fs.length : 0;
        });

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: hours.map(h => `${String(h).padStart(2,'0')}h`),
                datasets: [{
                    label: 'Average Delay (min)',
                    data: delays.map(v=>+v.toFixed(1)),
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
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v => v+' min' } }
                }
            }
        });
    }

    function buildDelayDistribution() {
        const ctx = $('chart-delay-distribution');
        if (!ctx) return;

        const buckets = [
            { label: 'On time (0)', count: 0 },
            { label: '1–15 min', count: 0 },
            { label: '16–30 min', count: 0 },
            { label: '31–60 min', count: 0 },
            { label: '61–120 min', count: 0 },
            { label: '>120 min', count: 0 },
        ];

        ALL_FLIGHTS.forEach(f => {
            const d = f.totalDelay;
            if (d === 0) buckets[0].count++;
            else if (d <= 15) buckets[1].count++;
            else if (d <= 30) buckets[2].count++;
            else if (d <= 60) buckets[3].count++;
            else if (d <= 120) buckets[4].count++;
            else buckets[5].count++;
        });

        const total = ALL_FLIGHTS.length;
        const colors = [T.safeDim, T.cyanDim, T.warnDim, T.warnDim, T.redDim, T.redDim];
        const bords  = [T.safe, T.cyan, T.warn, T.warn, T.red, T.red];

        new Chart(ctx, {
            type: 'doughnut',
            data: {
                labels: buckets.map(b=>b.label),
                datasets: [{
                    data: buckets.map(b=>b.count),
                    backgroundColor: colors,
                    borderColor: bords,
                    borderWidth: 1.5,
                    hoverOffset: 6,
                }]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                cutout: '55%',
                plugins: {
                    legend: { position: 'right', labels: { boxWidth: 10, padding: 14, font: { size: 10 } } },
                    tooltip: { callbacks: { label: c => ` ${c.label}: ${c.parsed} flights (${(c.parsed/total*100).toFixed(1)}%)` } }
                }
            }
        });
    }

    // ─── WEATHER VIEW CHARTS ──────────────────────────────────
    function buildWeatherSeries() {
        const ctx = $('chart-weather-series');
        if (!ctx) return;

        const pts = Array.from({length:49}, (_,i)=>i);
        const times = pts.map(i => {
            const h = Math.floor(i/2);
            const m = i%2 === 0 ? '00':'30';
            return `${String(h%24).padStart(2,'0')}:${m}`;
        });
        const wind = pts.map(i => +(20 + Math.sin(i*0.2)*12 + rnd(-3,5)).toFixed(1));
        const vis  = pts.map(i => +(6 + Math.cos(i*0.15)*3 + rnd(-0.5,0.5)).toFixed(1));
        const rain = pts.map(i => Math.max(0, +(3 + Math.sin(i*0.3+2)*3 + rnd(-1,3)).toFixed(1)));

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: times,
                datasets: [
                    {
                        label: 'Wind (km/h)',
                        data: wind,
                        borderColor: T.cyan,
                        backgroundColor: 'rgba(0,212,255,0.05)',
                        fill: true,
                        tension: 0.4,
                        borderWidth: 2,
                        pointRadius: 0,
                        yAxisID: 'y',
                    },
                    {
                        label: 'Visibility (km)',
                        data: vis,
                        borderColor: T.safe,
                        backgroundColor: 'transparent',
                        fill: false,
                        tension: 0.4,
                        borderWidth: 2,
                        pointRadius: 0,
                        yAxisID: 'y2',
                    },
                    {
                        label: 'Precipitation (mm/h)',
                        data: rain,
                        borderColor: T.warn,
                        backgroundColor: 'rgba(245,158,11,0.06)',
                        fill: true,
                        tension: 0.4,
                        borderWidth: 2,
                        pointRadius: 0,
                        yAxisID: 'y3',
                    }
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

    function buildRainImpact() {
        const ctx = $('chart-rain-impact');
        if (!ctx) return;

        const buckets = [0,2,4,6,8,10,14,20];
        const labels = buckets.slice(0,-1).map((b,i)=>`${b}–${buckets[i+1]} mm/h`);
        const avgDelays = buckets.slice(0,-1).map((b,i)=>{
            const fs = ALL_FLIGHTS.filter(f => f.weather.rain >= b && f.weather.rain < buckets[i+1]);
            return fs.length ? fs.reduce((s,f)=>s+f.totalDelay,0)/fs.length : 0;
        });

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'Average Delay (min)',
                    data: avgDelays.map(v=>+v.toFixed(1)),
                    backgroundColor: avgDelays.map((_,i) => i < 2 ? T.safeDim : i < 4 ? T.warnDim : T.redDim),
                    borderColor: avgDelays.map((_,i) => i < 2 ? T.safe : i < 4 ? T.warn : T.red),
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
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v=>v+' min' } }
                }
            }
        });
    }

    function buildGustScatter() {
        const ctx = $('chart-gust-scatter');
        if (!ctx) return;

        const pts = ALL_FLIGHTS.slice(0, 150).map(f => ({ x: f.weather.windGust, y: f.totalDelay }));

        new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p => p.y > 60 ? T.red : p.y > 20 ? T.warn : T.cyan),
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
                    tooltip: { callbacks: { label: c => ` Gust: ${c.raw.x} km/h | Delay: ${c.raw.y} min` } }
                },
                scales: {
                    x: { grid:{color:T.grid}, ticks:{color:T.textMuted}, title:{display:true,text:'Wind Gust (km/h)',color:T.textMuted,font:{size:10}} },
                    y: { grid:{color:T.grid}, ticks:{color:T.textMuted}, title:{display:true,text:'Delay (min)',color:T.textMuted,font:{size:10}} }
                }
            }
        });
    }

    function buildVisibility() {
        const ctx = $('chart-visibility');
        if (!ctx) return;

        const buckets = [1,2,3,4,6,8,10];
        const labels  = buckets.slice(0,-1).map((b,i)=>`${b}–${buckets[i+1]} km`);
        const avgD    = buckets.slice(0,-1).map((b,i) => {
            const fs = ALL_FLIGHTS.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i+1]);
            return fs.length ? fs.reduce((s,f)=>s+f.totalDelay,0)/fs.length : 0;
        });
        const cancel  = buckets.slice(0,-1).map((b,i) => {
            const fs = ALL_FLIGHTS.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i+1]);
            return fs.length ? fs.filter(f=>f.status==='cancelled').length/fs.length*100 : 0;
        });

        new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    {
                        label: 'Average Delay (min)',
                        data: avgD.map(v=>+v.toFixed(1)),
                        borderColor: T.warn,
                        fill: false,
                        tension: 0.4,
                        borderWidth: 2,
                        pointBackgroundColor: T.warn,
                        pointRadius: 4,
                        yAxisID: 'y',
                    },
                    {
                        label: '% Cancellations',
                        data: cancel.map(v=>+v.toFixed(1)),
                        borderColor: T.red,
                        fill: false,
                        tension: 0.4,
                        borderWidth: 2,
                        borderDash: [4,3],
                        pointBackgroundColor: T.red,
                        pointRadius: 4,
                        yAxisID: 'y2',
                    }
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { labels: { boxWidth: 10, font: { size: 10 } } } },
                scales: {
                    x: { grid:{color:T.grid}, ticks:{color:T.textMuted} },
                    y:  { grid:{color:T.grid}, ticks:{color:T.warn,callback:v=>v+' min'}, position:'left' },
                    y2: { ticks:{color:T.red,callback:v=>v+'%'}, position:'right', grid:{drawOnChartArea:false} }
                }
            }
        });
    }

    // ─── RISK FLIGHTS TABLE ───────────────────────────────────
    function renderRiskFlights() {
        const tbody = $('risk-flights-body');
        if (!tbody) return;

        const riskFlights = [...ALL_FLIGHTS]
            .sort((a,b)=>b.delayProb-a.delayProb)
            .slice(0, 12);

        tbody.innerHTML = riskFlights.map(f => {
            const pct = f.delayProb * 100;
            const fillCls = pct > 60 ? 'high' : pct > 35 ? 'mid' : 'low';
            const dCls = f.totalDelay > 60 ? 'd-high' : f.totalDelay > 20 ? 'd-mid' : 'd-low';
            const statusLabel = { 'on-time':'On time', delayed:'Delayed', critical:'Critical', cancelled:'Cancelled' }[f.status] || f.status;
            return `
            <tr>
                <td><span class="flight-code">${f.code}</span></td>
                <td class="route-cell">${f.origin.code}<span class="route-arrow">→</span>${f.destination.code}</td>
                <td class="mono" style="color:var(--t1)">${f.departure}</td>
                <td><span class="aircraft-badge">${f.aircraft}</span></td>
                <td class="mono" style="color:var(--cyan)">${f.weather.wind} km/h</td>
                <td class="mono" style="color:${f.weather.vis<3?'var(--red)':'var(--warn)'}">${f.weather.vis} km</td>
                <td class="prob-bar-cell">
                    <div class="prob-bar-wrap">
                        <div class="prob-bar-track"><div class="prob-bar-fill ${fillCls}" style="width:${pct.toFixed(0)}%"></div></div>
                        <span class="prob-pct mono" style="color:${fillCls==='high'?'var(--red)':fillCls==='mid'?'var(--warn)':'var(--safe)'}">${pct.toFixed(0)}%</span>
                    </div>
                </td>
                <td><span class="delay-val ${dCls}">${fmtMin(f.totalDelay)}</span></td>
                <td><span class="status-badge ${f.status}">${statusLabel}</span></td>
            </tr>`;
        }).join('');
    }

    // ─── FULL FLIGHTS TABLE ───────────────────────────────────
    let currentPage = 1;
    const PAGE_SIZE = 20;
    let filteredFlights = [...ALL_FLIGHTS];

    function renderFlightsTable() {
        const tbody = $('flights-table-body');
        if (!tbody) return;

        const start = (currentPage - 1) * PAGE_SIZE;
        const slice = filteredFlights.slice(start, start + PAGE_SIZE);
        const totalPages = Math.ceil(filteredFlights.length / PAGE_SIZE);

        $('flights-count').textContent = `${filteredFlights.length} flights loaded`;
        $('page-info').textContent = `${currentPage} / ${totalPages}`;
        $('btn-prev').disabled = currentPage === 1;
        $('btn-next').disabled = currentPage === totalPages || totalPages === 0;

        const statusLabel = s => ({ 'on-time':'On time', delayed:'Delayed', critical:'Critical', cancelled:'Cancelled' }[s]||s);
        const dClass = d => d > 60 ? 'd-high' : d > 15 ? 'd-mid' : 'd-low';

        tbody.innerHTML = slice.map((f, i) => `
            <tr>
                <td class="mono" style="color:var(--t2)">${start+i+1}</td>
                <td><span class="flight-code">${f.code}</span></td>
                <td style="color:var(--t1)">${f.origin.code}</td>
                <td style="color:var(--t1)">${f.destination.code}</td>
                <td class="mono">${f.departure}</td>
                <td class="mono">${f.arrival}</td>
                <td><span class="aircraft-badge">${f.aircraft}</span></td>
                <td><span class="delay-val ${dClass(f.depDelay)}">${fmtMin(f.depDelay)}</span></td>
                <td><span class="delay-val ${dClass(f.arrDelay)}">${fmtMin(f.arrDelay)}</span></td>
                <td><span class="delay-val ${dClass(f.totalDelay)}">${fmtMin(f.totalDelay)}</span></td>
                <td><span class="status-badge ${f.status}">${statusLabel(f.status)}</span></td>
            </tr>`).join('');
    }

    // ─── CLOCK ────────────────────────────────────────────────
    function updateClock() {
        const el = $('live-time');
        const ts = $('footer-ts');
        const now = new Date();
        const str = now.toTimeString().slice(0,8);
        if (el) el.textContent = str;
        if (ts) ts.textContent = now.toLocaleDateString('en-US',{day:'2-digit',month:'short',year:'numeric'}) + ' ' + str;
    }

    // ─── VIEW SWITCHING ───────────────────────────────────────
    let chartsBuilt = {
        overview: false, airports: false, weather: false, flights: false
    };

    function activateView(view) {
        document.querySelectorAll('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.view === view));
        document.querySelectorAll('.view-panel').forEach(p => p.classList.toggle('active', p.id === `view-${view}`));

        if (view === 'overview' && !chartsBuilt.overview) {
            buildAirportBar();
            buildCausesPie();
            buildTrendArea();
            buildWindScatter();
            buildWeatherRadar();
            renderRiskFlights();
            chartsBuilt.overview = true;
        }
        if (view === 'airports' && !chartsBuilt.airports) {
            buildAirportsRanking();
            buildHourBar();
            buildDelayDistribution();
            chartsBuilt.airports = true;
        }
        if (view === 'weather' && !chartsBuilt.weather) {
            buildWeatherSeries();
            buildRainImpact();
            buildGustScatter();
            buildVisibility();
            chartsBuilt.weather = true;
        }
        if (view === 'flights' && !chartsBuilt.flights) {
            renderFlightsTable();
            chartsBuilt.flights = true;
        }
    }

    // ─── INIT ─────────────────────────────────────────────────
    function init() {
        // Nav buttons
        document.querySelectorAll('.nav-btn').forEach(btn => {
            btn.addEventListener('click', () => activateView(btn.dataset.view));
        });

        // Pagination
        $('btn-prev')?.addEventListener('click', () => {
            if (currentPage > 1) { currentPage--; renderFlightsTable(); }
        });
        $('btn-next')?.addEventListener('click', () => {
            const total = Math.ceil(filteredFlights.length / PAGE_SIZE);
            if (currentPage < total) { currentPage++; renderFlightsTable(); }
        });

        // Time pills
        document.querySelectorAll('.tpill').forEach(btn => {
            btn.addEventListener('click', () => {
                document.querySelectorAll('.tpill').forEach(b => b.classList.remove('active'));
                btn.classList.add('active');
            });
        });

        // Filters on flights view
        ['filter-origin', 'filter-status', 'filter-aircraft'].forEach(id => {
            $(id)?.addEventListener('change', () => {
                const ori = $('filter-origin')?.value || '';
                const sta = $('filter-status')?.value || '';
                const acft = $('filter-aircraft')?.value || '';
                filteredFlights = ALL_FLIGHTS.filter(f => {
                    if (ori && !f.origin.name.includes(ori.split('—')[1]?.trim() || ori)) return false;
                    const statusLabels = { 'on-time':'On time', delayed:'Delayed', critical:'Delayed', cancelled:'Cancelled' };
                    if (sta && statusLabels[f.status] !== sta) return false;
                    if (acft && f.aircraft !== acft) return false;
                    return true;
                });
                currentPage = 1;
                renderFlightsTable();
            });
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