/* ════════════════════════════════════════════════════════════════════════════
   SKYDELAY · PREDICTION MAP — Leaflet view with anime.js polish
   ─────────────────────────────────────────────────────────────────────────────
   · Shows ONLY predicted flights from flight_predictions table
   · Up to 5 closest-to-now predictions per airport popup
   · Teardrop pin markers coloured by worst predicted delay (blue→purple)
   · Two tile styles: clean (CARTO voyager) and cartoon (Stamen Watercolor)
   ════════════════════════════════════════════════════════════════════════════ */

(() => {
    'use strict';

    // ── Tile URLs ────────────────────────────────────────────────────────────
    const ATTR =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' +
        ' · &copy; <a href="https://carto.com/">CARTO</a>';

    const ATTR_WATERCOLOR =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OpenStreetMap</a>' +
        ' · Map tiles by <a href="http://stamen.com">Stamen Design</a>';

    const TILES = {
        clean: {
            base:   'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}{r}.png',
            labels: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png',
        },
        cartoon: {
            base:   'https://tiles.stadiamaps.com/tiles/stamen_watercolor/{z}/{x}/{y}.jpg',
            labels: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png',
        }
    };

    let map, baseLayer, labelLayer;
    let markers = [];
    let isCartoonMode = false;
    let iataLookup = new Map();   // ICAO → IATA for destination display

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

        baseLayer = L.tileLayer(TILES.clean.base, {
            attribution: ATTR,
            subdomains: 'abcd',
            maxZoom: 19,
        }).addTo(map);

        labelLayer = L.tileLayer(TILES.clean.labels, {
            subdomains: 'abcd',
            maxZoom: 19,
            attribution: '',
        }).addTo(map);

        addStyleToggle();
        animateChrome();
        loadMapData();
    }

    // ── Style toggle (clean ↔ cartoon watercolor) ────────────────────────────
    function addStyleToggle() {
        const StyleToggle = L.Control.extend({
            options: { position: 'topright' },
            onAdd() {
                const btn = L.DomUtil.create('button', 'style-toggle');
                btn.id = 'styleToggleBtn';
                btn.title = 'Toggle map style';
                btn.textContent = '🎨';
                L.DomEvent.on(btn, 'click', toggleStyle);
                L.DomEvent.disableClickPropagation(btn);
                return btn;
            }
        });
        new StyleToggle().addTo(map);
    }

    function toggleStyle() {
        isCartoonMode = !isCartoonMode;
        const btn = document.getElementById('styleToggleBtn');

        anime({
            targets: btn,
            rotate: [0, 360],
            scale:  [1, 0.85, 1],
            duration: 480,
            easing: 'easeOutBack',
            complete: () => { btn.style.transform = ''; }
        });
        btn.textContent = isCartoonMode ? '🗺️' : '🎨';

        document.body.classList.toggle('cartoon-mode', isCartoonMode);

        const theme = isCartoonMode ? 'cartoon' : 'clean';
        baseLayer.setUrl(TILES[theme].base);
        labelLayer.setUrl(TILES[theme].labels);

        // Update attribution
        baseLayer.options.attribution = isCartoonMode ? ATTR_WATERCOLOR : ATTR;
        map.attributionControl._container.innerHTML = '';
        map.attributionControl._update();
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

    // ── Severity bucketing from predictions ──────────────────────────────────
    function bucketCounts(predictions) {
        return predictions.reduce((acc, p) => {
            const k = (p.prediction || 'none').toLowerCase();
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

    // Category label for display
    const CATEGORY_LABELS = {
        none:     'On Time',
        low:      'Low',
        moderate: 'Moderate',
        severe:   'Severe',
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

    function delayCategoryClass(cat) {
        const k = (cat || 'none').toLowerCase();
        if (k === 'severe')   return 'd-sev';
        if (k === 'moderate') return 'd-mod';
        if (k === 'low')      return 'd-low';
        return 'd-none';
    }

    function fmtScheduledTime(isoStr) {
        if (!isoStr) return '—';
        try {
            const d = new Date(isoStr);
            return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
                + ' · ' + d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
        } catch { return isoStr; }
    }

    // ── Build the popup HTML (prediction-focused) ────────────────────────────
    function buildPopupHtml(airport, weather, predictions) {
        const counts   = bucketCounts(predictions);
        const severity = severityFromCounts(counts);
        const accentRgb = ACCENTS[severity].rgb;
        const total    = predictions.length;

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
        const summaryText = `${total} prediction${total !== 1 ? 's' : ''}` + (parts.length ? ` · ${parts.join(' · ')}` : '');

        const rows = predictions.length > 0
            ? predictions.map(p => {
                const destCode = p.dest ? (iataLookup.get(p.dest) || p.dest) : '—';
                const catKey = (p.prediction || 'none').toLowerCase();
                const catLabel = CATEGORY_LABELS[catKey] || catKey;
                return `
                  <tr>
                    <td class="flight-id">${escapeHtml(p.flight || '—')}</td>
                    <td class="route-cell">→ ${escapeHtml(destCode)}</td>
                    <td class="time-cell">${fmtScheduledTime(p.time)}</td>
                    <td class="delay-cat ${delayCategoryClass(catKey)}">${catLabel}</td>
                  </tr>`;
              }).join('')
            : `<tr><td colspan="4" class="empty-row">No predictions available.</td></tr>`;

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
                        <th>Flight</th><th>Dest</th><th>Scheduled</th><th>Delay</th>
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
            const dataPayload = await dataRes.json();   // Now contains predictions per airport
            const weatherList = weatherRes.ok ? await weatherRes.json() : [];

            const byIcao = new Map(dataPayload.map(d => [d.icao, d]));

            // Build ICAO → IATA lookup for destination display in popups
            airports.forEach(a => { if (a.iata) iataLookup.set(a.icao, a.iata); });
            dataPayload.forEach(d => { if (d.iata) iataLookup.set(d.icao, d.iata); });

            airports.forEach((airport, idx) => {
                const enriched = byIcao.get(airport.icao) || {};
                const predictions = enriched.predictions ?? [];
                const severity = enriched.severity || severityFromCounts(bucketCounts(predictions));

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
                    predictions,
                };

                marker.bindPopup('', {
                    maxWidth: 400,
                    minWidth: 380,
                    className: 'sky-popup-wrap',
                    closeButton: true,
                    autoPan: true,
                    autoPanPadding: [30, 30],
                });

                marker.on('popupopen', e => {
                    const { airport, weather, predictions } = marker.airportData;
                    e.popup.setContent(buildPopupHtml(airport, weather, predictions));
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

    // ── Marker pin-stab entrance (anime.js) ─────────────────────────────────
    function cascadeMarkerEntrance() {
        const els = markers
            .map(m => m.marker.getElement()?.querySelector('.sky-pin'))
            .filter(Boolean);

        const shadows = markers
            .map(m => m.marker.getElement()?.querySelector('.sky-pin-shadow'))
            .filter(Boolean);

        els.forEach(el => {
            el.style.opacity = 0;
            el.style.transformOrigin = 'center bottom';
        });
        shadows.forEach(el => { el.style.opacity = 0; });

        anime({
            targets: els,
            translateY: [
                { value: [-220, 0], duration: 420, easing: 'easeInCubic' },
            ],
            scaleY: [
                { value: 1, duration: 420 },
                { value: 0.68, duration: 80, easing: 'easeOutQuad' },
                { value: 1, duration: 500, easing: 'easeOutElastic(1, .45)' },
            ],
            scaleX: [
                { value: 1, duration: 420 },
                { value: 1.25, duration: 80, easing: 'easeOutQuad' },
                { value: 1, duration: 500, easing: 'easeOutElastic(1, .45)' },
            ],
            opacity: [
                { value: [0, 1], duration: 120, easing: 'linear' },
            ],
            delay: anime.stagger(40, { from: 'center' }),
            complete: () => {
                els.forEach(el => { el.style.transform = ''; });
            }
        });

        anime({
            targets: shadows,
            opacity: [0, 1],
            scaleX: [2.5, 1],
            scaleY: [2.5, 1],
            duration: 350,
            delay: anime.stagger(40, { from: 'center', start: 400 }),
            easing: 'easeOutQuad',
            complete: () => {
                shadows.forEach(el => { el.style.transform = ''; });
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
