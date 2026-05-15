/* ════════════════════════════════════════════════════════════════════════════
   SKYDELAY · PREDICTION MAP + FLIGHTS
   ─────────────────────────────────────────────────────────────────────────────
   · Map pins coloured by Delay Index (1 − OTP), a 0–1 continuous scale
   · Up to 5 closest-to-now predictions per airport popup
   · Flights tab: full searchable/filterable predictions table
   · Two tile styles: clean (CARTO voyager) + sketch (Stamen Toner Lite)
   ════════════════════════════════════════════════════════════════════════════ */

(() => {
    'use strict';

    /* ── Tile URLs ──────────────────────────────────────────────────────── */
    const ATTR =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>' +
        ' · &copy; <a href="https://carto.com/">CARTO</a>';

    const ATTR_SKETCH =
        '&copy; <a href="https://www.openstreetmap.org/copyright">OSM</a>' +
        ' · <a href="https://stadiamaps.com/">Stadia</a>';

    const TILES = {
        clean: {
            base:   'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_nolabels/{z}/{x}/{y}{r}.png',
            labels: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png',
        },
        sketch: {
            base:   'https://tiles.stadiamaps.com/tiles/stamen_toner_lite/{z}/{x}/{y}{r}.png',
            labels: 'https://{s}.basemaps.cartocdn.com/rastertiles/voyager_only_labels/{z}/{x}/{y}{r}.png',
        }
    };

    let map, baseLayer, labelLayer;
    let markers = [];
    let isSketchMode = false;
    let iataLookup = new Map();

    /* ── Colour interpolation for 0–1 delay rate ────────────────────── */
    // 0.0 → light sky blue (#81D4FA)  rgb(129,212,250)
    // 0.5 → indigo/purple   (#7C4DFF)  rgb(124,77,255)
    // 1.0 → deep purple     (#4A148C)  rgb(74,20,140)
    const GRADIENT_STOPS = [
        { t: 0.0, r: 129, g: 212, b: 250 },
        { t: 0.3, r: 92,  g: 107, b: 192 },
        { t: 0.6, r: 142, g: 36,  b: 170 },
        { t: 1.0, r: 74,  g: 20,  b: 140 },
    ];

    function delayRateToRgb(rate) {
        const t = Math.max(0, Math.min(1, rate));
        let lo = GRADIENT_STOPS[0], hi = GRADIENT_STOPS[GRADIENT_STOPS.length - 1];
        for (let i = 0; i < GRADIENT_STOPS.length - 1; i++) {
            if (t >= GRADIENT_STOPS[i].t && t <= GRADIENT_STOPS[i + 1].t) {
                lo = GRADIENT_STOPS[i];
                hi = GRADIENT_STOPS[i + 1];
                break;
            }
        }
        const f = hi.t === lo.t ? 0 : (t - lo.t) / (hi.t - lo.t);
        const r = Math.round(lo.r + (hi.r - lo.r) * f);
        const g = Math.round(lo.g + (hi.g - lo.g) * f);
        const b = Math.round(lo.b + (hi.b - lo.b) * f);
        return { r, g, b, css: `rgb(${r},${g},${b})`, str: `${r},${g},${b}` };
    }

    const CATEGORY_LABELS = {
        none: 'On Time', low: 'Low', moderate: 'Moderate', severe: 'Severe',
    };

    /* ── Tab switching ───────────────────────────────────────────────── */
    function initTabs() {
        document.querySelectorAll('.tab-btn').forEach(btn => {
            btn.addEventListener('click', () => {
                const tab = btn.dataset.tab;
                document.querySelectorAll('.tab-btn').forEach(b => b.classList.remove('active'));
                document.querySelectorAll('.tab-view').forEach(v => v.classList.remove('active'));
                btn.classList.add('active');
                document.getElementById(`view-${tab}`).classList.add('active');

                if (tab === 'map' && map) {
                    setTimeout(() => map.invalidateSize(), 100);
                }
                if (tab === 'flights' && !flightsLoaded) {
                    loadFlightsData();
                }
            });
        });
    }

    /* ── Info modal ──────────────────────────────────────────────────── */
    function initInfoModal() {
        const btn = document.getElementById('infoBtn');
        const backdrop = document.getElementById('infoModalBackdrop');
        const close = document.getElementById('infoModalClose');

        btn.addEventListener('click', () => backdrop.classList.add('visible'));
        close.addEventListener('click', () => backdrop.classList.remove('visible'));
        backdrop.addEventListener('click', e => {
            if (e.target === backdrop) backdrop.classList.remove('visible');
        });
    }

    /* ══════════════════════════════════════════════════════════════════
       MAP
       ══════════════════════════════════════════════════════════════════ */
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

    /* ── Style toggle (clean ↔ sketch) ─────────────────────────────── */
    function addStyleToggle() {
        const Toggle = L.Control.extend({
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
        new Toggle().addTo(map);
    }

    function toggleStyle() {
        isSketchMode = !isSketchMode;
        const btn = document.getElementById('styleToggleBtn');
        anime({
            targets: btn,
            rotate: [0, 360],
            scale: [1, 0.85, 1],
            duration: 480,
            easing: 'easeOutBack',
            complete: () => { btn.style.transform = ''; }
        });
        btn.textContent = isSketchMode ? '🗺️' : '🎨';
        document.body.classList.toggle('sketch-mode', isSketchMode);

        const theme = isSketchMode ? 'sketch' : 'clean';
        baseLayer.setUrl(TILES[theme].base);
        labelLayer.setUrl(TILES[theme].labels);
    }

    /* ── Chrome entrance animations ────────────────────────────────── */
    function animateChrome() {
        anime({ targets: '#mapOverlay', opacity: [0, 1], translateY: [-12, 0], duration: 700, easing: 'easeOutQuart' });
        anime({ targets: '#mapLegend', opacity: [0, 1], translateX: [-12, 0], duration: 700, delay: 200, easing: 'easeOutQuart' });
    }

    /* ── Severity helpers (for popup distribution bar) ─────────────── */
    function bucketCounts(predictions) {
        return predictions.reduce((acc, p) => {
            const k = (p.prediction || 'none').toLowerCase();
            if (k in acc) acc[k]++; else acc.none++;
            return acc;
        }, { none: 0, low: 0, moderate: 0, severe: 0 });
    }

    /* ── Pin marker ────────────────────────────────────────────────── */
    function createPinIcon(delayRate, iata, isPulsing) {
        const colour = delayRateToRgb(delayRate);
        const html = `
            <div class="sky-pin" style="--pin-fill:${colour.css}">
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
            className: 'sky-pin-icon',
            iconSize: [32, 44],
            iconAnchor: [16, 44],
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

    function fmtTime(isoStr) {
        if (!isoStr) return '—';
        try {
            const d = new Date(isoStr);
            return d.toLocaleTimeString('en-GB', { hour: '2-digit', minute: '2-digit' })
                + ' · ' + d.toLocaleDateString('en-GB', { day: '2-digit', month: 'short' });
        } catch { return isoStr; }
    }

    /* ── Popup HTML ────────────────────────────────────────────────── */
    function buildPopupHtml(airport, weather, predictions, delayRate) {
        const counts   = bucketCounts(predictions);
        const colour   = delayRateToRgb(delayRate);
        const total    = predictions.length;

        const tempStr = weather && Number.isFinite(weather.temp)    ? `${weather.temp.toFixed(1)}°C`            : '—';
        const windStr = weather && Number.isFinite(weather.windSpeed) ? `${weather.windSpeed.toFixed(1)} m/s`     : '—';
        const visStr  = weather && Number.isFinite(weather.visibility) ? `${(weather.visibility / 1000).toFixed(0)} km` : '—';

        // Distribution bar
        const distSegs = [
            { key: 'none', count: counts.none },
            { key: 'low', count: counts.low },
            { key: 'moderate', count: counts.moderate },
            { key: 'severe', count: counts.severe },
        ].filter(s => s.count > 0)
         .map(s => `<div class="dist-seg" data-c="${s.key}" style="flex:${s.count}" title="${s.count} ${s.key}"></div>`)
         .join('');

        const parts = [];
        if (counts.none > 0)     parts.push(`${counts.none} on time`);
        if (counts.low > 0)      parts.push(`${counts.low} low`);
        if (counts.moderate > 0) parts.push(`${counts.moderate} mod`);
        if (counts.severe > 0)   parts.push(`${counts.severe} sev`);
        const summaryText = `${total} prediction${total !== 1 ? 's' : ''}` + (parts.length ? ` · ${parts.join(' · ')}` : '');

        const rows = predictions.length > 0
            ? predictions.map(p => {
                const destCode = p.dest ? (iataLookup.get(p.dest) || p.dest) : '—';
                const catKey   = (p.prediction || 'none').toLowerCase();
                const catLabel = CATEGORY_LABELS[catKey] || catKey;
                return `
                  <tr>
                    <td class="flight-id">${esc(p.flight || '—')}</td>
                    <td class="route-cell">→ ${esc(destCode)}</td>
                    <td class="time-cell">${fmtTime(p.time)}</td>
                    <td class="delay-cat ${delayCategoryClass(catKey)}">${catLabel}</td>
                  </tr>`;
              }).join('')
            : `<tr><td colspan="4" class="empty-row">No predictions available.</td></tr>`;

        return `
          <div class="sky-popup" style="--accent-rgb:${colour.str}">
            <div class="sky-popup-head">
                <div class="sky-popup-top-row">
                    <div>
                        <span class="sky-popup-name">${esc(airport.name)}</span>
                        <div class="sky-popup-codes">
                            <span class="sky-code-badge">${airport.iata || ''}</span>
                            <span class="sky-code-badge">${airport.icao}</span>
                        </div>
                    </div>
                    <div class="sky-popup-rate">
                        <span class="rate-value">${delayRate.toFixed(2)}</span>
                        <span class="rate-label">Delay Index</span>
                    </div>
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

    function esc(str) {
        return String(str).replace(/[&<>"']/g, c =>
            ({ '&':'&amp;', '<':'&lt;', '>':'&gt;', '"':'&quot;', "'":'&#39;' }[c]));
    }

    /* ── Load map data ─────────────────────────────────────────────── */
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

            airports.forEach(a => { if (a.iata) iataLookup.set(a.icao, a.iata); });
            dataPayload.forEach(d => { if (d.iata) iataLookup.set(d.icao, d.iata); });

            airports.forEach((airport, idx) => {
                const enriched    = byIcao.get(airport.icao) || {};
                const predictions = enriched.predictions ?? [];
                const delayRate   = typeof enriched.delayRate === 'number' ? enriched.delayRate : 0;

                const latestWeather = weatherList
                    .filter(w => w.icao === airport.icao)
                    .pop() ?? null;

                const icon = createPinIcon(
                    delayRate,
                    airport.iata || enriched.iata || '',
                    delayRate >= 0.6
                );

                const lat = airport.lat;
                const lon = airport.lon ?? airport.lng;

                const marker = L.marker([lat, lon], { icon, riseOnHover: true }).addTo(map);

                marker.airportData = {
                    airport: { ...airport, iata: airport.iata || enriched.iata, name: airport.name || enriched.name },
                    weather: latestWeather,
                    predictions,
                    delayRate,
                };

                marker.bindPopup('', {
                    maxWidth: 400, minWidth: 380,
                    className: 'sky-popup-wrap',
                    closeButton: true, autoPan: true,
                    autoPanPadding: [30, 30],
                });

                marker.on('popupopen', e => {
                    const { airport, weather, predictions, delayRate } = marker.airportData;
                    e.popup.setContent(buildPopupHtml(airport, weather, predictions, delayRate));
                    requestAnimationFrame(() => {
                        const node = e.popup.getElement()?.querySelector('.sky-popup');
                        if (!node) return;
                        anime({ targets: node, scale: [0.92, 1], opacity: [0, 1], duration: 360, easing: 'easeOutBack' });
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

    /* ── Marker pin-stab animation ─────────────────────────────────── */
    function cascadeMarkerEntrance() {
        const els = markers.map(m => m.marker.getElement()?.querySelector('.sky-pin')).filter(Boolean);
        const shadows = markers.map(m => m.marker.getElement()?.querySelector('.sky-pin-shadow')).filter(Boolean);

        els.forEach(el => { el.style.opacity = 0; el.style.transformOrigin = 'center bottom'; });
        shadows.forEach(el => { el.style.opacity = 0; });

        anime({
            targets: els,
            translateY: [{ value: [-220, 0], duration: 420, easing: 'easeInCubic' }],
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
            opacity: [{ value: [0, 1], duration: 120, easing: 'linear' }],
            delay: anime.stagger(40, { from: 'center' }),
            complete: () => { els.forEach(el => { el.style.transform = ''; }); }
        });

        anime({
            targets: shadows,
            opacity: [0, 1], scaleX: [2.5, 1], scaleY: [2.5, 1],
            duration: 350,
            delay: anime.stagger(40, { from: 'center', start: 400 }),
            easing: 'easeOutQuad',
            complete: () => { shadows.forEach(el => { el.style.transform = ''; }); }
        });
    }

    function hideLoader() {
        const loader = document.getElementById('loader');
        if (!loader) return;
        anime({ targets: loader, opacity: [1, 0], duration: 500, easing: 'easeOutQuad', complete: () => loader.remove() });
    }

    /* ══════════════════════════════════════════════════════════════════
       FLIGHTS TAB
       ══════════════════════════════════════════════════════════════════ */
    let flightsLoaded = false;
    let allPredictions = [];
    let filteredPredictions = [];
    let flightsPage = 0;
    const PAGE_SIZE = 30;

    async function loadFlightsData() {
        try {
            const res = await fetch('/api/predictions');
            if (!res.ok) throw new Error('Predictions API error');
            allPredictions = await res.json();
            flightsLoaded = true;

            populateFilterDropdowns();
            applyFlightsFilter();
        } catch (err) {
            console.error('Flights load error:', err);
        }
    }

    function populateFilterDropdowns() {
        const origins = new Set();
        const dests   = new Set();
        allPredictions.forEach(p => {
            if (p.origin) origins.add(p.origin);
            if (p.dest)   dests.add(p.dest);
        });

        const originSel = document.getElementById('filterOrigin');
        const destSel   = document.getElementById('filterDest');

        [...origins].sort().forEach(code => {
            const label = iataLookup.get(code) || code;
            originSel.insertAdjacentHTML('beforeend', `<option value="${code}">${label} — ${code}</option>`);
        });
        [...dests].sort().forEach(code => {
            const label = iataLookup.get(code) || code;
            destSel.insertAdjacentHTML('beforeend', `<option value="${code}">${label} — ${code}</option>`);
        });
    }

    function applyFlightsFilter() {
        const flightQ  = document.getElementById('filterFlight').value.trim().toLowerCase();
        const originQ  = document.getElementById('filterOrigin').value;
        const destQ    = document.getElementById('filterDest').value;
        const catQ     = document.getElementById('filterCategory').value;

        filteredPredictions = allPredictions.filter(p => {
            if (flightQ  && !(p.flight || '').toLowerCase().includes(flightQ)) return false;
            if (originQ  && p.origin !== originQ) return false;
            if (destQ    && p.dest   !== destQ)   return false;
            if (catQ     && (p.prediction || '').toLowerCase() !== catQ) return false;
            return true;
        });

        flightsPage = 0;
        renderFlightsTable();
    }

    function renderFlightsTable() {
        const tbody = document.getElementById('flightsTableBody');
        const total = filteredPredictions.length;
        const pages = Math.max(1, Math.ceil(total / PAGE_SIZE));
        const start = flightsPage * PAGE_SIZE;
        const slice = filteredPredictions.slice(start, start + PAGE_SIZE);

        document.getElementById('flightsCount').textContent = `${total} prediction${total !== 1 ? 's' : ''}`;
        document.getElementById('pageInfo').textContent = `${flightsPage + 1} / ${pages}`;
        document.getElementById('btnPrev').disabled = flightsPage === 0;
        document.getElementById('btnNext').disabled = flightsPage >= pages - 1;

        if (slice.length === 0) {
            tbody.innerHTML = '<tr><td colspan="7" class="empty-row">No predictions match your filters.</td></tr>';
            return;
        }

        tbody.innerHTML = slice.map((p, i) => {
            const catKey   = (p.prediction || 'none').toLowerCase();
            const catLabel = CATEGORY_LABELS[catKey] || catKey;
            const originLabel = iataLookup.get(p.origin) || p.origin || '—';
            const destLabel   = iataLookup.get(p.dest)   || p.dest   || '—';
            return `
                <tr>
                    <td class="row-num">${start + i + 1}</td>
                    <td class="flight-id">${esc(p.flight || '—')}</td>
                    <td>${esc(originLabel)}</td>
                    <td>${esc(destLabel)}</td>
                    <td class="time-cell">${fmtTime(p.time)}</td>
                    <td class="delay-cat ${delayCategoryClass(catKey)}">${catLabel}</td>
                    <td class="time-cell">${fmtTime(p.lastUpdated)}</td>
                </tr>`;
        }).join('');
    }

    function initFlightsListeners() {
        document.getElementById('filterFlight').addEventListener('input', applyFlightsFilter);
        document.getElementById('filterOrigin').addEventListener('change', applyFlightsFilter);
        document.getElementById('filterDest').addEventListener('change', applyFlightsFilter);
        document.getElementById('filterCategory').addEventListener('change', applyFlightsFilter);
        document.getElementById('btnPrev').addEventListener('click', () => { flightsPage--; renderFlightsTable(); });
        document.getElementById('btnNext').addEventListener('click', () => { flightsPage++; renderFlightsTable(); });
    }

    /* ══════════════════════════════════════════════════════════════════
       BOOT
       ══════════════════════════════════════════════════════════════════ */
    document.addEventListener('DOMContentLoaded', () => {
        initTabs();
        initInfoModal();
        initFlightsListeners();
        initMap();
    });

})();
