/* ============================================
   SKYDELAY — app.js
   Flight Delay Intelligence Dashboard
   Charts mirror datamart.db + .events schema:
     flight_features(flight_id, origin_icao, dest_icao,
                     temp, wind, gust, vis,
                     distance_km, departure_delay, delay_category)
     weather_records(airport_icao, temp, wind_speed,
                     wind_gust, visibility, timestamp)
     flight events:  flightId, origin, destination, date,
                     departureTimeUTC, arrivalTimeUTC, status,
                     departureDelay, arrivalDelay,
                     distanceKm, aircraftModel
     weather events: icao, description, temp, feelsLike,
                     humidity, visibility, windSpeed,
                     windGust, cloudsPct
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
    const fmtMin = m => m === 0 ? '0 min' : (m > 0 ? `+${Math.round(m)} min` : `${Math.round(m)} min`);

    // Pearson correlation on two numeric arrays
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

    // Pre-bucketed delay category as used by DatamartManager.categorize()
    function delayCategory(min) {
        if (min <= 15) return 'none';
        if (min <= 30) return 'low';
        if (min <= 60) return 'moderate';
        return 'severe';
    }

    // ─── AIRPORTS (IATA + ICAO + name) ──────────────────────
    // Mirrors what AirportCodeTranslator emits for the Spanish set
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

    // Aircraft list — taken from the .events file samples
    const AIRCRAFT = ['Airbus A320', 'Airbus A320 Neo', 'Airbus A321 Neo', 'Boeing B737-800', 'ATR 72-600', 'Embraer E195'];
    const AIRLINES = [
        { name: 'Iberia',          code: 'IB' },
        { name: 'Vueling',         code: 'VY' },
        { name: 'Air Europa',      code: 'UX' },
        { name: 'Ryanair',         code: 'FR' },
        { name: 'Binter Canarias', code: 'NT' },
        { name: 'EasyJet',         code: 'U2' },
    ];

    // ─── REMOTE STATE ────────────────────────────────────────
    let DATA = {
        flights: [],          // shaped like flight_features rows
        weatherSeries: [],    // weather_records for LEMD
        useReal: false,
    };

    // ─── REMOTE FETCH (with mock fallback) ───────────────────
    async function loadReal() {
        try {
            const [featRes, wxRes] = await Promise.all([
                fetch('/api/flight-features'),
                fetch('/api/weather-series?icao=LEMD&limit=200'),
            ]);
            if (!featRes.ok || !wxRes.ok) throw new Error('API not ready');

            const features = await featRes.json();
            const wx = await wxRes.json();

            if (Array.isArray(features) && features.length > 0) {
                DATA.flights = enrichFromFeatures(features);
                DATA.useReal = true;
            }
            if (Array.isArray(wx) && wx.length > 0) {
                DATA.weatherSeries = wx;
            }
        } catch (e) {
            // backend not available — keep mocks
        }
    }

    // Convert flight_features rows into the shape charts expect
    function enrichFromFeatures(rows) {
        return rows.map((r, i) => {
            const ori = AIRPORTS.find(a => a.icao === r.originIcao) || { iata: r.originIcao || '???', icao: r.originIcao || '????', name: r.originIcao || '???' };
            const dst = AIRPORTS.find(a => a.icao === r.destIcao) || { iata: r.destIcao || '???', icao: r.destIcao || '????', name: r.destIcao || '???' };
            const cat = r.delayCategory || delayCategory(r.departureDelay);
            const status = cat === 'severe' ? 'Cancelled' : (cat === 'none' ? 'Landed' : 'Delayed');
            return {
                id: i + 1,
                flightId: r.flightId,
                airline: r.flightId?.split(' ')[0] || 'Unknown',
                aircraft: pickAircraftBy(r.distanceKm),
                origin: ori,
                destination: dst,
                date: '—',
                departure: '—:—',
                arrival: '—:—',
                distanceKm: r.distanceKm || 0,
                depDelay: r.departureDelay || 0,
                arrDelay: 0,                            // arrival delay not in flight_features
                category: cat,
                status,
                weather: {
                    temp:     r.temp || 0,
                    wind:     r.wind || 0,             // m/s as stored
                    gust:     r.gust || 0,
                    vis:      (r.vis || 10000) / 1000, // metres → km
                    cloudsPct: rndInt(0, 100),         // not in flight_features
                    humidity:  rndInt(30, 90),         // not in flight_features
                }
            };
        });
    }

    function pickAircraftBy(km) {
        if (km > 1500) return 'Airbus A321 Neo';
        if (km > 800)  return 'Airbus A320 Neo';
        if (km > 400)  return 'Airbus A320';
        return 'ATR 72-600';
    }

    // ─── GENERATE MOCK FLIGHTS (shape matches flight events) ─
    function genFlights(n = 280) {
        const flights = [];
        for (let i = 0; i < n; i++) {
            const ori = AIRPORTS[rndInt(0, AIRPORTS.length - 1)];
            let dst;
            do { dst = AIRPORTS[rndInt(0, AIRPORTS.length - 1)]; } while (dst.iata === ori.iata);

            const airline = AIRLINES[rndInt(0, AIRLINES.length - 1)];
            const acft = AIRCRAFT[rndInt(0, AIRCRAFT.length - 1)];
            const depH = rndInt(5, 23);
            const depM = rndInt(0, 59);
            const duration = rndInt(60, 240);

            // weather snapshot — matches weather event fields
            const wind     = +rnd(0, 18).toFixed(2);    // m/s (matches OWM)
            const gust     = wind > 4 ? +(wind + rnd(0, 8)).toFixed(2) : 0;
            const visKm    = +(rnd(1, 10)).toFixed(1);
            const temp     = +rnd(2, 36).toFixed(1);
            const humidity = rndInt(30, 95);
            const cloudsPct = rndInt(0, 100);

            // Probability of a delay weighted by weather (we have no causes,
            // so we just simulate the actual correlations)
            let p = 0.08;
            if (wind > 10)        p += 0.18;
            if (gust > 14)        p += 0.18;
            if (visKm < 3)        p += 0.22;
            if (cloudsPct > 80)   p += 0.05;
            if (humidity > 85)    p += 0.05;
            p = clamp(p + rnd(-0.04, 0.10), 0.04, 0.96);

            const delayed = Math.random() < p;
            const depDelay = delayed ? rndInt(16, 180) : rndInt(0, 14);
            const arrDelay = delayed ? depDelay + rndInt(-15, 25) : rndInt(-20, 10);

            const category = delayCategory(depDelay);

            // Status uses the .events value set
            let status = 'Landed';
            if (Math.random() < 0.02) status = 'Cancelled';
            else if (category !== 'none') status = 'Delayed';

            const distanceKm = rndInt(150, 2400);

            flights.push({
                id: i + 1,
                flightId: `${airline.name} ${airline.code}${rndInt(1000, 9999)}`,
                airline: airline.name,
                aircraft: acft,
                origin: ori,
                destination: dst,
                date: '05. May 2026',
                departure: `${String(depH).padStart(2,'0')}:${String(depM).padStart(2,'0')}`,
                arrival:   `${String(Math.floor((depH * 60 + depM + duration) / 60) % 24).padStart(2,'0')}:${String((depM + duration) % 60).padStart(2,'0')}`,
                distanceKm,
                depDelay, arrDelay,
                category, status,
                weather: { temp, wind, gust, vis: visKm, humidity, cloudsPct }
            });
        }
        return flights;
    }

    // ─── AIRPORT BAR CHART ───────────────────────────────────
    function buildAirportBar() {
        const ctx = $('chart-airport-bar');
        if (!ctx) return;

        const labels = AIRPORTS.map(a => a.iata);
        const depDelays = AIRPORTS.map(a => {
            const f = DATA.flights.filter(x => x.origin.iata === a.iata);
            return f.length ? f.reduce((s, x) => s + Math.max(0, x.depDelay), 0) / f.length : 0;
        });
        const arrDelays = AIRPORTS.map(a => {
            const f = DATA.flights.filter(x => x.destination.iata === a.iata);
            return f.length ? f.reduce((s, x) => s + Math.max(0, x.arrDelay), 0) / f.length : 0;
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

    // ─── FEATURE IMPACT TORNADO (replaces Delay Causes pie) ──
    // Innovation: instead of fictitious causes, we compute the
    // actual correlation between each captured variable and the
    // departure delay. Big bars = bulk drivers, small bars = the
    // finely-impacting ones — both visible at once.
    function buildFeatureImpact() {
        const ctx = $('chart-feature-impact');
        if (!ctx) return;

        const delays = DATA.flights.map(f => f.depDelay);
        const features = [
            { label: 'Wind speed',  key: f => f.weather.wind,     unit: 'm/s' },
            { label: 'Wind gust',   key: f => f.weather.gust,     unit: 'm/s' },
            { label: 'Visibility',  key: f => -f.weather.vis,     unit: 'km'  }, // inverted: low vis → more delay
            { label: 'Cloud cover', key: f => f.weather.cloudsPct, unit: '%'   },
            { label: 'Humidity',    key: f => f.weather.humidity, unit: '%'   },
            { label: 'Temperature', key: f => Math.abs(f.weather.temp - 18), unit: '°C dev' },
            { label: 'Distance',    key: f => f.distanceKm,       unit: 'km'  },
        ];

        const corrs = features.map(f => {
            const xs = DATA.flights.map(f.key);
            return { label: f.label, unit: f.unit, r: pearson(xs, delays) };
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

        new Chart(ctx, {
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
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted },
                        min: -1, max: 1,
                        title: { display: true, text: 'Pearson r vs departure delay', color: T.textMuted, font: { size: 10 } }
                    },
                    y: { grid: { color: T.grid }, ticks: { color: T.text } }
                }
            }
        });
    }

    // ─── SEVERITY STACKED AREA (replaces single-line trend) ──
    // True area chart: counts of flights per delay_category per hour.
    function buildSeverityArea() {
        const ctx = $('chart-severity-area');
        if (!ctx) return;

        const hours = Array.from({ length: 24 }, (_, i) => `${String(i).padStart(2,'0')}:00`);
        const cats = ['none', 'low', 'moderate', 'severe'];
        const byCat = Object.fromEntries(cats.map(c => [c, new Array(24).fill(0)]));

        DATA.flights.forEach(f => {
            const h = parseInt(f.departure, 10);
            if (Number.isNaN(h)) return;
            byCat[f.category][h]++;
        });

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: hours,
                datasets: [
                    { label: 'None',     data: byCat.none,     fill: true, borderColor: T.safe, backgroundColor: 'rgba(16,185,129,0.18)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Low',      data: byCat.low,      fill: true, borderColor: T.cyan, backgroundColor: 'rgba(0,212,255,0.18)',  borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Moderate', data: byCat.moderate, fill: true, borderColor: T.warn, backgroundColor: 'rgba(245,158,11,0.20)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                    { label: 'Severe',   data: byCat.severe,   fill: true, borderColor: T.red,  backgroundColor: 'rgba(255,77,106,0.22)', borderWidth: 1.5, pointRadius: 0, tension: 0.4, stack: 'a' },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
                interaction: { mode: 'index', intersect: false },
                plugins: { legend: { display: false } },
                scales: {
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted, maxTicksLimit: 12 } },
                    y: {
                        stacked: true,
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted, callback: v => v + ' flights' }
                    }
                }
            }
        });
    }

    // ─── WIND SCATTER ─────────────────────────────────────────
    function buildWindScatter() {
        const ctx = $('chart-wind-scatter');
        if (!ctx) return;

        const pts = DATA.flights.slice(0, 160).map(f => ({
            x: f.weather.wind,
            y: Math.max(0, f.depDelay),
        }));

        new Chart(ctx, {
            type: 'scatter',
            data: {
                datasets: [{
                    label: 'Flight',
                    data: pts,
                    pointBackgroundColor: pts.map(p =>
                        p.y > 60 ? T.red : p.y > 30 ? T.warn : T.safe
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
                            label: c => ` Wind: ${c.raw.x.toFixed(1)} m/s | Delay: ${c.raw.y} min`
                        }
                    }
                },
                scales: {
                    x: {
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted },
                        title: { display: true, text: 'Wind speed (m/s)', color: T.textMuted, font: { size: 10 } }
                    },
                    y: {
                        grid: { color: T.grid },
                        ticks: { color: T.textMuted },
                        title: { display: true, text: 'Departure Delay (min)', color: T.textMuted, font: { size: 10 } }
                    }
                }
            }
        });
    }

    // ─── WEATHER RADAR CHART ──────────────────────────────────
    function buildWeatherRadar() {
        const ctx = $('chart-weather-radar');
        if (!ctx) return;

        // Use averages from the data we actually have for the current snapshot
        const avg = (k) => DATA.flights.length
            ? DATA.flights.reduce((s, f) => s + k(f), 0) / DATA.flights.length
            : 0;

        const wind = avg(f => f.weather.wind);
        const gust = avg(f => f.weather.gust);
        const visInv = 10 - avg(f => f.weather.vis); // invert: high score = bad
        const cloud = avg(f => f.weather.cloudsPct);
        const humid = avg(f => f.weather.humidity);
        const tempDev = Math.abs(avg(f => f.weather.temp) - 18);

        // Scale each to 0..100 against an operational max
        const scale = (v, max) => clamp(v / max * 100, 0, 100);

        new Chart(ctx, {
            type: 'radar',
            data: {
                labels: ['Wind', 'Gust', 'Low Visibility', 'Clouds', 'Humidity', 'Temp Dev'],
                datasets: [
                    {
                        label: 'Current Conditions',
                        data: [
                            scale(wind, 20),
                            scale(gust, 25),
                            scale(visInv, 10),
                            cloud,
                            humid,
                            scale(tempDev, 20),
                        ].map(v => +v.toFixed(1)),
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
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata || f.destination.iata === a.iata);
            const avg = fs.length ? fs.reduce((s, f) => s + Math.max(0, f.depDelay) + Math.max(0, f.arrDelay), 0) / fs.length : 0;
            return { code: a.iata, avg, count: fs.length };
        }).sort((a, b) => b.avg - a.avg);

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: data.map(d => d.code),
                datasets: [{
                    label: 'Average accumulated delay (min)',
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
                    tooltip: {
                        callbacks: {
                            label: c => ` ${c.parsed.x.toFixed(1)} min · ${data[c.dataIndex].count} flights`
                        }
                    }
                },
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
            const fs = DATA.flights.filter(f => parseInt(f.departure, 10) === h);
            return fs.length ? fs.reduce((s,f)=>s+Math.max(0,f.depDelay),0)/fs.length : 0;
        });

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels: hours.map(h => `${String(h).padStart(2,'0')}h`),
                datasets: [{
                    label: 'Average Departure Delay (min)',
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

    // ─── AIRPORT × SEVERITY HEATMAP (replaces a 2nd pie) ─────
    // Innovation: shows the share of flights in each delay_category
    // per airport. Equivalent to a Sankey [airport → category]
    // but easier to scan and stays in the brand grid aesthetic.
    function buildAirportSeverityHeatmap() {
        const root = $('heatmap-airport-severity');
        if (!root) return;

        const cats = ['none', 'low', 'moderate', 'severe'];
        const catColor = {
            none:     T.safe,
            low:      T.cyan,
            moderate: T.warn,
            severe:   T.red,
        };

        // Build header row
        let html = `<div class="heatmap-head" style="text-align:left;padding-left:0.4rem">Airport</div>`;
        cats.forEach(c => { html += `<div class="heatmap-head">${c}</div>`; });

        AIRPORTS.forEach(a => {
            const fs = DATA.flights.filter(f => f.origin.iata === a.iata);
            const total = fs.length || 1;
            const pcts = cats.map(c => fs.filter(f => f.category === c).length / total);

            html += `<div class="heatmap-row-label">${a.iata}<span style="color:var(--t2);margin-left:0.4rem;font-size:0.6rem">${fs.length}</span></div>`;
            pcts.forEach((p, i) => {
                const intensity = clamp(p * 1.6, 0.05, 0.85); // emphasise small values too
                const color = catColor[cats[i]];
                const rgb = color === T.safe ? '16,185,129'
                          : color === T.cyan ? '0,212,255'
                          : color === T.warn ? '245,158,11'
                          : '255,77,106';
                const pct = (p * 100).toFixed(0);
                html += `
                  <div class="heatmap-cell" style="background: rgba(${rgb}, ${intensity}); border-color: rgba(${rgb}, ${clamp(intensity + 0.15, 0, 0.9)})">
                    <span class="hm-val" style="color:${p > 0.4 ? '#fff' : color}">${pct}%</span>
                    <span class="hm-tt">${a.iata} · ${cats[i]} → ${pct}% (${fs.filter(f => f.category === cats[i]).length}/${fs.length})</span>
                  </div>`;
            });
        });

        root.innerHTML = html;
    }

    // ─── WEATHER VIEW CHARTS ──────────────────────────────────
    function buildWeatherSeries() {
        const ctx = $('chart-weather-series');
        if (!ctx) return;

        // Prefer real /api/weather-series if we have it
        let times, wind, gust, vis, temp;
        if (DATA.weatherSeries.length > 0) {
            const series = [...DATA.weatherSeries].reverse();
            times = series.map(r => r.timestamp ? r.timestamp.slice(11, 16) : '');
            temp = series.map(r => +(r.temp || 0).toFixed(1));
            wind = series.map(r => +(r.windSpeed || 0).toFixed(1));
            gust = series.map(r => +(r.windGust || 0).toFixed(1));
            vis  = series.map(r => +((r.visibility || 0) / 1000).toFixed(1));
        } else {
            const pts = Array.from({length:48}, (_,i)=>i);
            times = pts.map(i => {
                const h = Math.floor(i/2);
                const m = i%2 === 0 ? '00':'30';
                return `${String(h%24).padStart(2,'0')}:${m}`;
            });
            wind = pts.map(i => +(8 + Math.sin(i*0.2)*5 + rnd(-1.5,2.5)).toFixed(1));
            gust = pts.map((_,i) => +(wind[i] + rnd(0,6)).toFixed(1));
            vis  = pts.map(i => +(7 + Math.cos(i*0.15)*2.5 + rnd(-0.5,0.5)).toFixed(1));
            temp = pts.map(i => +(18 + Math.sin(i*0.13)*7 + rnd(-1,1)).toFixed(1));
        }

        new Chart(ctx, {
            type: 'line',
            data: {
                labels: times,
                datasets: [
                    { label: 'Wind (m/s)', data: wind, borderColor: T.cyan, backgroundColor: 'rgba(0,212,255,0.05)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y' },
                    { label: 'Gust (m/s)', data: gust, borderColor: T.red, backgroundColor: 'transparent', fill: false, tension: 0.4, borderWidth: 2, borderDash: [4,3], pointRadius: 0, yAxisID: 'y' },
                    { label: 'Visibility (km)', data: vis, borderColor: T.safe, backgroundColor: 'transparent', fill: false, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y2' },
                    { label: 'Temp (°C)', data: temp, borderColor: T.warn, backgroundColor: 'rgba(245,158,11,0.05)', fill: true, tension: 0.4, borderWidth: 2, pointRadius: 0, yAxisID: 'y3' },
                ]
            },
            options: {
                responsive: true,
                maintainAspectRatio: false,
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

    // ─── CLOUDINESS IMPACT (replaces Rain Impact — no rain field) ─
    function buildCloudImpact() {
        const ctx = $('chart-cloud-impact');
        if (!ctx) return;

        const buckets = [0, 20, 40, 60, 80, 101];
        const labels = ['0–20', '20–40', '40–60', '60–80', '80–100'];
        const avgDelays = buckets.slice(0,-1).map((b,i)=>{
            const fs = DATA.flights.filter(f => f.weather.cloudsPct >= b && f.weather.cloudsPct < buckets[i+1]);
            return fs.length ? fs.reduce((s,f)=>s+Math.max(0,f.depDelay),0)/fs.length : 0;
        });

        new Chart(ctx, {
            type: 'bar',
            data: {
                labels,
                datasets: [{
                    label: 'Average Departure Delay (min)',
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
                    x: { grid: { color: T.grid }, ticks: { color: T.textMuted }, title: { display: true, text: 'Cloud cover (%)', color: T.textMuted, font: { size: 10 } } },
                    y: { grid: { color: T.grid }, ticks: { color: T.textMuted, callback: v=>v+' min' } }
                }
            }
        });
    }

    function buildGustScatter() {
        const ctx = $('chart-gust-scatter');
        if (!ctx) return;

        const pts = DATA.flights.slice(0, 200).map(f => ({ x: f.weather.gust, y: Math.max(0, f.depDelay) }));

        new Chart(ctx, {
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
                    x: { grid:{color:T.grid}, ticks:{color:T.textMuted}, title:{display:true,text:'Wind gust (m/s)',color:T.textMuted,font:{size:10}} },
                    y: { grid:{color:T.grid}, ticks:{color:T.textMuted}, title:{display:true,text:'Delay (min)',color:T.textMuted,font:{size:10}} }
                }
            }
        });
    }

    function buildVisibility() {
        const ctx = $('chart-visibility');
        if (!ctx) return;

        const buckets = [0, 2, 4, 6, 8, 11];
        const labels  = buckets.slice(0,-1).map((b,i)=>`${b}–${buckets[i+1]} km`);
        const avgD    = buckets.slice(0,-1).map((b,i) => {
            const fs = DATA.flights.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i+1]);
            return fs.length ? fs.reduce((s,f)=>s+Math.max(0,f.depDelay),0)/fs.length : 0;
        });
        const cancel  = buckets.slice(0,-1).map((b,i) => {
            const fs = DATA.flights.filter(f => f.weather.vis >= b && f.weather.vis < buckets[i+1]);
            return fs.length ? fs.filter(f=>f.status==='Cancelled').length/fs.length*100 : 0;
        });

        new Chart(ctx, {
            type: 'line',
            data: {
                labels,
                datasets: [
                    { label: 'Average Delay (min)', data: avgD.map(v=>+v.toFixed(1)), borderColor: T.warn, fill: false, tension: 0.4, borderWidth: 2, pointBackgroundColor: T.warn, pointRadius: 4, yAxisID: 'y' },
                    { label: '% Cancellations',     data: cancel.map(v=>+v.toFixed(1)), borderColor: T.red, fill: false, tension: 0.4, borderWidth: 2, borderDash: [4,3], pointBackgroundColor: T.red, pointRadius: 4, yAxisID: 'y2' }
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

        // Score: combine delay magnitude + adverse weather
        const scored = DATA.flights.map(f => ({
            f,
            score: Math.max(0, f.depDelay) * 0.6
                 + f.weather.wind * 1.5
                 + f.weather.gust * 0.8
                 + (10 - f.weather.vis) * 4
        }));
        const top = scored.sort((a, b) => b.score - a.score).slice(0, 12).map(s => s.f);

        const sevClass = c => ({ none:'s-none', low:'s-low', moderate:'s-mod', severe:'s-sev' }[c] || '');
        const dCls = d => d > 60 ? 'd-high' : d > 15 ? 'd-mid' : 'd-low';

        tbody.innerHTML = top.map(f => `
            <tr>
                <td><span class="flight-code">${f.flightId}</span></td>
                <td class="route-cell">${f.origin.iata}<span class="route-arrow">→</span>${f.destination.iata}</td>
                <td class="mono" style="color:var(--t1)">${f.departure}</td>
                <td><span class="aircraft-badge">${f.aircraft}</span></td>
                <td class="mono" style="color:var(--cyan)">${f.weather.wind.toFixed(1)}</td>
                <td class="mono" style="color:${f.weather.gust > 14 ? 'var(--red)' : 'var(--warn)'}">${f.weather.gust.toFixed(1)}</td>
                <td class="mono" style="color:${f.weather.vis < 3 ? 'var(--red)' : 'var(--safe)'}">${f.weather.vis.toFixed(1)}</td>
                <td><span class="delay-val ${dCls(f.depDelay)}">${fmtMin(f.depDelay)}</span></td>
                <td><span class="status-badge ${sevClass(f.category)}">${f.category}</span></td>
            </tr>`).join('');
    }

    // ─── FULL FLIGHTS TABLE ───────────────────────────────────
    let currentPage = 1;
    const PAGE_SIZE = 20;
    let filteredFlights = [];

    function renderFlightsTable() {
        const tbody = $('flights-table-body');
        if (!tbody) return;

        const start = (currentPage - 1) * PAGE_SIZE;
        const slice = filteredFlights.slice(start, start + PAGE_SIZE);
        const totalPages = Math.max(1, Math.ceil(filteredFlights.length / PAGE_SIZE));

        $('flights-count').textContent = `${filteredFlights.length} flights loaded${DATA.useReal ? ' · live' : ''}`;
        $('page-info').textContent = `${currentPage} / ${totalPages}`;
        $('btn-prev').disabled = currentPage === 1;
        $('btn-next').disabled = currentPage === totalPages || totalPages === 0;

        const statusClass = s => ({ 'Landed':'on-time', 'Delayed':'delayed', 'Cancelled':'cancelled' }[s] || 'critical');
        const dClass = d => d > 60 ? 'd-high' : d > 15 ? 'd-mid' : 'd-low';

        tbody.innerHTML = slice.map((f, i) => `
            <tr>
                <td class="mono" style="color:var(--t2)">${start+i+1}</td>
                <td><span class="flight-code">${f.flightId}</span></td>
                <td style="color:var(--t1)">${f.origin.iata}</td>
                <td style="color:var(--t1)">${f.destination.iata}</td>
                <td class="mono" style="color:var(--t2)">${f.date}</td>
                <td class="mono">${f.departure}</td>
                <td class="mono">${f.arrival}</td>
                <td><span class="aircraft-badge">${f.aircraft}</span></td>
                <td class="mono" style="color:var(--t2)">${f.distanceKm} km</td>
                <td><span class="delay-val ${dClass(f.depDelay)}">${fmtMin(f.depDelay)}</span></td>
                <td><span class="delay-val ${dClass(f.arrDelay)}">${fmtMin(f.arrDelay)}</span></td>
                <td><span class="status-badge ${statusClass(f.status)}">${f.status}</span></td>
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
    let chartsBuilt = { overview: false, airports: false, weather: false, flights: false };

    function activateView(view) {
        document.querySelectorAll('.nav-btn').forEach(b => b.classList.toggle('active', b.dataset.view === view));
        document.querySelectorAll('.view-panel').forEach(p => p.classList.toggle('active', p.id === `view-${view}`));

        if (view === 'overview' && !chartsBuilt.overview) {
            buildAirportBar();
            buildFeatureImpact();
            buildSeverityArea();
            buildWindScatter();
            buildWeatherRadar();
            renderRiskFlights();
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
            buildCloudImpact();
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
    async function init() {
        // Try to pull real data first
        await loadReal();

        // If the real dataset is too small to visualise (we only have ~21
        // rows right now), enrich with mock flights that share the same
        // shape — keeps charts readable while real data accumulates.
        if (DATA.flights.length < 50) {
            DATA.flights = [...DATA.flights, ...genFlights(280)];
        }
        filteredFlights = [...DATA.flights];

        // Live status chip
        const statusChip = $('data-status');
        if (statusChip) {
            statusChip.textContent = DATA.useReal ? 'LIVE DATA' : 'DEMO DATA';
        }

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

        // Filters on flights view
        ['filter-origin', 'filter-status', 'filter-aircraft'].forEach(id => {
            $(id)?.addEventListener('change', () => {
                const ori = $('filter-origin')?.value || '';
                const sta = $('filter-status')?.value || '';
                const acft = $('filter-aircraft')?.value || '';
                filteredFlights = DATA.flights.filter(f => {
                    if (ori && f.origin.iata !== ori) return false;
                    if (sta && f.status !== sta) return false;
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
