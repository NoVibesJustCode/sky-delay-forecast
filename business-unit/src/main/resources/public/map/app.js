/* ════════════════════════════════════════════════════════════════════════════
   SKYDELAY · LIVE MAP — professional Leaflet view with anime.js polish
   ─────────────────────────────────────────────────────────────────────────────
   · Full-world labels (pins shown only for Spanish airports)
   · Classic teardrop pin markers, severity-coloured (blue→purple gradient)
   · Animated entrance (cascade drop-in) + smooth dark-mode tile swap
   · Professional popup with rich flights table
   ════════════════════════════════════════════════════════════════════════════ */

(() => {
    'use strict';

    // ── Tile URLs ────────────────────────────────────────────────────────────
    const ATTR =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' +
        ' · &copy; <a href="https://carto.com/">CARTO</a>';

    const TILES = {
        light: {
            base:   'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}{r}.png',
            labels: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png',
        },
        dark: {
            base:   'https://{s}.basemaps.cartocdn.com/dark_matter_nolabels/{z}/{x}/{y}{r}.png',
            labels: 'https://{s}.basemaps.cartocdn.com/dark_matter_only_labels/{z}/{x}/{y}{r}.png',
        }
    };

    let map, baseLayer;
    let labelLayer;
    let markers = [];
    let isDarkMode = false;

    // ── Init ──────────────────────────────────────────────────────────────────
    function initMap() {
        map = L.map('map', {
            zoomControl: true,
            zoomAnimation: true,
            fadeAnimation: true,
            attributionControl: true,
        }).setView([38.8, -4.5], 5);

        L.control.zoom({ position: 'topleft' }).remove();
        map.zoomControl.setPosition('bottomright');

        baseLayer = L.tileLayer(TILES.light.base, {
            attribution: ATTR,
            subdomains: 'abcd',
            maxZoom: 19,
        }).addTo(map);

        labelLayer = L.tileLayer(TILES.light.labels, {
            subdomains: 'abcd',
            maxZoom: 19,
            attribution: '',
        }).addTo(map);

        addDarkModeToggle();
        animateChrome();
        loadMapData();
    }

    // ── Dark-mode toggle (tile swap + theme class) ───────────────────────────
    function addDarkModeToggle() {
        const DarkToggle = L.Control.extend({
            options: { position: 'topright' },
            onAdd() {
                const btn = L.DomUtil.create('button', 'dark-toggle');
                btn.id = 'darkToggleBtn';
                btn.title = 'Toggle dark mode';
                btn.textContent = '🌙';
                L.DomEvent.on(btn, 'click', toggleDark);
                L.DomEvent.disableClickPropagation(btn);
                return btn;
            }
        });
        new DarkToggle().addTo(map);
    }

    function toggleDark() {
        isDarkMode = !isDarkMode;
        const btn = document.getElementById('darkToggleBtn');

        anime({
            targets: btn,
            rotate: [0, 360],
            scale:  [1, 0.85, 1],
            duration: 480,
            easing: 'easeOutBack',
            complete: () => { btn.style.transform = ''; }
        });
        btn.textContent = isDarkMode ? '☀️' : '🌙';

        document.body.classList.toggle('dark-mode', isDarkMode);

        // Swap actual tile layers for proper dark-mode rendering
        const theme = isDarkMode ? 'dark' : 'light';
        baseLayer.setUrl(TILES[theme].base);
        labelLayer.setUrl(TILES[theme].labels);
    }

    // ── Overlay & legend entrance (anime.js) ──────────────────────────────────
    function animateChrome() {
        anime({
            targets: '#mapOverlay',
            opacity: [0, 1],
            translateY: [-12, 0],
            duration: 700,
            easing: 'easeOutQuart'
        });
        anime({
            targets: '#mapLegend',
            opacity: [0, 1],
            translateX: [-12, 0],
            duration: 700,
            delay: 200,
            easing: 'easeOutQuart'
        });
    }

    // ── Severity bucketing & colour mapping ───────────────────────────────────
    function bucketCounts(flights) {
        return flights.reduce((acc, f) => {
            const k = (f.category || f.prediction || 'none').toLowerCase();
            if (k in acc) acc[k]++;
            else          acc.none++;
            return acc;
        }, { none: 0, low: 0, moderate: 0, severe: 0 });
    }

    function severityFromCounts(c) {
        if (c.severe > 0)   return 'severe';
        if (c.moderate > 0) return 'moderate';
        if (c.low > 0)      return 'low';
        return 'none';
    }

    // RGB accent values for popup gradients/badges (blue→purple storm palette)
    const ACCENTS = {
        none:     { rgb: '129,212,250' },   // light sky blue
        low:      { rgb: '92,107,192'  },   // indigo
        moderate: { rgb: '142,36,170'  },   // purple
        severe:   { rgb: '74,20,140'   },   // deep storm purple
    };

    // ── Teardrop pin marker (SVG-based, severity-coloured) ───────────────────
    function createPinIcon(severity, iata, isPulsing) {
        const html = `
            <div class="sky-pin" data-severity="${severity}">
                ${isPulsing ? '<div class="sky-pin-pulse"></div>' : ''}
                <div class="sky-pin-body">
                    <svg class="sky-pin-svg" xmlns="http://www.w3.org/2000/svg"
                         viewBox="0 0 32 44" width="22" height="30">
                        <path class="sky-pin-path"
                              d="M16 1.5 C8.5 1.5 2 8 2 16 C2 27 16 43 16 43
                                 C16 43 30 27 30 16 C30 8 23.5 1.5 16 1.5 Z"/>
                        <circle class="sky-pin-dot" cx="16" cy="15" r="6"/>
                    </svg>
                </div>
                <div class="sky-pin-tag">${iata || ''}</div>
                <div class="sky-pin-shadow"></div>
            </div>`;

        return L.divIcon({
            html,
            className:   'sky-pin-icon',
            iconSize:    [32, 44],
            iconAnchor:  [16, 44],
            popupAnchor: [0, -44],
        });
    }

    function delayClass(min) {
        if (min <= 15) return 'd-none';
        if (min <= 30) return 'd-low';
        if (min <= 60) return 'd-mod';
        return 'd-sev';
    }

    function fmtNum(n, digits = 1) {
        return (typeof n === 'number' && Number.isFinite(n)) ? n.toFixed(digits) : '—';
    }

    function fmtVis(metres) {
        if (typeof metres !== 'number' || !Number.isFinite(metres)) return '—';
        return (metres / 1000).toFixed(1);
    }

    // ── Build the popup HTML (professional dark-glass style) ─────────────────
    function buildPopupHtml(airport, weather, flights) {
        const counts   = bucketCounts(flights);
        const severity = severityFromCounts(counts);
        const accentRgb = ACCENTS[severity].rgb;
        const total    = flights.length;

        const tempStr = weather && Number.isFinite(weather.temp)
            ? `${weather.temp.toFixed(1)}°C` : '—';
        const windStr = weather && Number.isFinite(weather.windSpeed)
            ? `${weather.windSpeed.toFixed(1)} m/s` : '—';
        const visStr  = weather && Number.isFinite(weather.visibility)
            ? `${(weather.visibility / 1000).toFixed(0)} km` : '—';

        // Distribution bar segments (only non-zero)
        const distSegs = [
            { key: 'none', count: counts.none },
            { key: 'low', count: counts.low },
            { key: 'moderate', count: counts.moderate },
            { key: 'severe', count: counts.severe },
        ].filter(s => s.count > 0)
         .map(s => `<div class="dist-seg" data-c="${s.key}" style="flex:${s.count}" title="${s.count} ${s.key}"></div>`)
         .join('');

        // Summary text
        const parts = [];
        if (counts.none > 0)     parts.push(`${counts.none} on time`);
        if (counts.low > 0)      parts.push(`${counts.low} low`);
        if (counts.moderate > 0) parts.push(`${counts.moderate} mod`);
        if (counts.severe > 0)   parts.push(`${counts.severe} sev`);
        const summaryText = `${total} flights` + (parts.length ? ` · ${parts.join(' · ')}` : '');

        const rows = flights.length > 0
            ? flights.map(f => {
                const delay = typeof f.delay === 'number' ? f.delay : parseInt(f.delay, 10) || 0;
                return `
                  <tr>
                    <td class="flight-id">${escapeHtml(f.flightId || (f.route?.split('→')?.[0]?.trim()) || '—')}</td>
                    <td class="route-cell">→ ${escapeHtml(f.dest || '—')}</td>
                    <td class="num">${fmtNum(f.wind)}</td>
                    <td class="num">${fmtVis(f.vis)}</td>
                    <td class="delay-val ${delayClass(delay)}">${delay >= 0 ? '+' : ''}${delay}′</td>
                  </tr>`;
              }).join('')
            : `<tr><td colspan="5" class="empty-row">No flights recorded yet.</td></tr>`;

        return `
          <div class="sky-popup" style="--accent-rgb:${accentRgb}">
            <div class="sky-popup-head">
                <div class="sky-popup-top-row">
                    <div>
                        <span class="sky-popup-name">${escapeHtml(airport.name)}</span>
                        <div class="sky-popup-codes">
                            <span class="sky-code-badge">${airport.iata || ''}</span>
                            <span class="sky-code-badge">${airport.icao}</span>
                        </div>
                    </div>
                    <span class="sky-popup-sev">${severity.toUpperCase()}</span>
                </div>
                <div class="sky-popup-weather">
                    <span><span class="weather-label">Temp</span> ${tempStr}</span>
                    <span><span class="weather-label">Wind</span> ${windStr}</span>
                    <span><span class="weather-label">Vis</span> ${visStr}</span>
                </div>
            </div>

            <div class="sky-popup-dist">
                <div class="dist-bar">${distSegs}</div>
                <div class="dist-summary">${summaryText}</div>
            </div>

            <div class="sky-popup-table">
                <table>
                    <thead><tr>
                        <th>Flight</th><th>Dest</th><th>Wind</th><th>Vis</th><th>Delay</th>
                    </tr></thead>
                    <tbody>${rows}</tbody>
                </table>
            </div>
          </div>`;
    }

    function escapeHtml(str) {
        return String(str).replace(/[&<>"']/g, c => ({
            '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;'
        }[c]));
    }

    // ── Data loading ──────────────────────────────────────────────────────────
    async function loadMapData() {
        try {
            const [airportsRes, dataRes, weatherRes] = await Promise.all([
                fetch('/api/airports'),
                fetch('/api/data'),
                fetch('/api/weather-records?limit=500')
            ]);

            if (!airportsRes.ok || !dataRes.ok) throw new Error('API error');

            const airports    = await airportsRes.json();
            const dataPayload = await dataRes.json();
            const weatherList = weatherRes.ok ? await weatherRes.json() : [];

            const byIcao = new Map(dataPayload.map(d => [d.icao, d]));

            airports.forEach((airport, idx) => {
                const enriched = byIcao.get(airport.icao) || {};
                const flights = enriched.recentFlights ?? [];
                const counts = bucketCounts(flights);
                const severity = severityFromCounts(counts);

                const latestWeather = weatherList
                    .filter(w => w.icao === airport.icao)
                    .pop() ?? null;

                const icon = createPinIcon(
                    severity,
                    airport.iata || enriched.iata || '',
                    severity === 'severe'
                );

                const lat = airport.lat;
                const lon = airport.lon ?? airport.lng;

                const marker = L.marker([lat, lon], { icon, riseOnHover: true })
                    .addTo(map);

                marker.airportData = {
                    airport: { ...airport, iata: airport.iata || enriched.iata, name: airport.name || enriched.name },
                    weather: latestWeather,
                    flights,
                };

                marker.bindPopup('', {
                    maxWidth: 380,
                    minWidth: 380,
                    className: 'sky-popup-wrap',
                    closeButton: true,
                    autoPan: true,
                    autoPanPadding: [30, 30],
                });

                marker.on('popupopen', e => {
                    const { airport, weather, flights } = marker.airportData;
                    e.popup.setContent(buildPopupHtml(airport, weather, flights));
                    requestAnimationFrame(() => {
                        const node = e.popup.getElement().querySelector('.sky-popup');
                        if (!node) return;
                        anime({
                            targets: node,
                            scale:   [0.92, 1],
                            opacity: [0, 1],
                            duration: 360,
                            easing: 'easeOutBack',
                        });
                    });
                });

                markers.push({ marker, idx });
            });

            cascadeMarkerEntrance();
            hideLoader();
        } catch (err) {
            console.error('Map data error:', err);
            hideLoader();
        }
    }

    // ── Marker drop-in cascade ────────────────────────────────────────────────
    function cascadeMarkerEntrance() {
        const els = markers
            .map(m => m.marker.getElement()?.querySelector('.sky-pin'))
            .filter(Boolean);
        els.forEach(el => {
            el.style.opacity = 0;
            el.style.transformOrigin = 'center bottom';
        });

        anime({
            targets: els,
            opacity: [0, 1],
            translateY: [-32, 0],
            scale: [0.4, 1],
            duration: 700,
            delay: anime.stagger(70, { from: 'center' }),
            easing: 'easeOutBack',
            complete: () => {
                // Clean up inline transforms left by anime.js
                els.forEach(el => { el.style.transform = ''; });
            }
        });
    }

    function hideLoader() {
        const loader = document.getElementById('loader');
        if (!loader) return;
        anime({
            targets: loader,
            opacity: [1, 0],
            duration: 500,
            easing: 'easeOutQuad',
            complete: () => loader.remove(),
        });
    }

    document.addEventListener('DOMContentLoaded', initMap);

})();
