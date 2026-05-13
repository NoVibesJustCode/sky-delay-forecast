(() => {
    'use strict';

    let map;

    function initMap() {
        map = L.map('map').setView([36.0, -5.0], 5);

        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; OpenStreetMap &copy; CARTO'
        }).addTo(map);

        loadMapData();
    }

    // Replica la lógica de color de AirportsLayer.java
    function getMarkerColor(severe, moderate, low) {
        if (severe > 0)   return { fill: '#ef4444', glow: '#ef444466', label: 'severe' };
        if (moderate > 0) return { fill: '#f97316', glow: '#f9731666', label: 'moderate' };
        if (low > 0)      return { fill: '#eab308', glow: '#eab30866', label: 'low' };
        return             { fill: '#22c55e', glow: '#22c55e66', label: 'none' };
    }

    function getCategoryColor(category) {
        const colors = {
            'none':     '#22c55e',
            'low':      '#eab308',
            'moderate': '#f97316',
            'severe':   '#ef4444'
        };
        return colors[category] || '#fff';
    }

    // Pin SVG estético: gota con borde blanco, punto interior y sombra sutil
    function createPinIcon(colorInfo, pulse) {
        const { fill, glow } = colorInfo;

        // Anillo de pulso solo para severe
        const pulseRing = pulse
            ? `<circle cx="16" cy="14" r="13" fill="none" stroke="${fill}" stroke-width="2" opacity="0.5">
                 <animate attributeName="r" values="13;20;13" dur="2s" repeatCount="indefinite"/>
                 <animate attributeName="opacity" values="0.5;0;0.5" dur="2s" repeatCount="indefinite"/>
               </circle>`
            : '';

        const svg = `
        <svg xmlns="http://www.w3.org/2000/svg" width="32" height="42" viewBox="0 0 32 42">
          <defs>
            <filter id="shadow-${fill.replace('#','')}" x="-40%" y="-20%" width="180%" height="180%">
              <feDropShadow dx="0" dy="2" stdDeviation="2" flood-color="${fill}" flood-opacity="0.5"/>
            </filter>
          </defs>
          ${pulseRing}
          <!-- Cuerpo del pin -->
          <path d="M16 2C9.37 2 4 7.37 4 14c0 9.33 12 26 12 26s12-16.67 12-26C28 7.37 22.63 2 16 2z"
                fill="${fill}" filter="url(#shadow-${fill.replace('#','')})" />
          <!-- Borde blanco -->
          <path d="M16 2C9.37 2 4 7.37 4 14c0 9.33 12 26 12 26s12-16.67 12-26C28 7.37 22.63 2 16 2z"
                fill="none" stroke="white" stroke-width="1.5" opacity="0.9"/>
          <!-- Círculo interior blanco -->
          <circle cx="16" cy="14" r="5" fill="white" opacity="0.95"/>
          <!-- Punto central de color -->
          <circle cx="16" cy="14" r="2.5" fill="${fill}"/>
        </svg>`;

        return L.divIcon({
            html: svg,
            className: '',
            iconSize:   [32, 42],
            iconAnchor: [16, 40],
            popupAnchor:[0, -38]
        });
    }

    async function loadMapData() {
        try {
            const [airportsRes, predictionsRes, weatherRes] = await Promise.all([
                fetch('/api/airports'),
                fetch('/api/data'),
                fetch('/api/weather-records')
            ]);

            if (!airportsRes.ok || !predictionsRes.ok)
                throw new Error('Error loading data from API');

            const airports    = await airportsRes.json();
            const predictions = await predictionsRes.json();
            const weatherList = weatherRes.ok ? await weatherRes.json() : [];

            airports.forEach(airport => {
                const icao = airport.icao;

                const airportPreds = predictions.filter(p =>
                    typeof p.route === 'string' && p.route.startsWith(icao)
                );
                const severe   = airportPreds.filter(p => p.prediction === 'severe').length;
                const moderate = airportPreds.filter(p => p.prediction === 'moderate').length;
                const low      = airportPreds.filter(p => p.prediction === 'low').length;

                const colorInfo = getMarkerColor(severe, moderate, low);
                const icon      = createPinIcon(colorInfo, severe > 0);

                const weatherRecords = weatherList.filter(w => w.icao === icao);
                const latestWeather  = weatherRecords.length > 0
                    ? weatherRecords[weatherRecords.length - 1]
                    : null;

                const weatherInfo = latestWeather
                    ? `${latestWeather.temp?.toFixed(1)}°C · ${latestWeather.windSpeed?.toFixed(1)} m/s`
                    : 'No weather data';

                const marker = L.marker([airport.lat, airport.lon ?? airport.lng], { icon })
                    .addTo(map);

                const predRows = airportPreds.length > 0
                    ? airportPreds.slice(0, 6).map(p => `
                        <tr>
                          <td style="padding:3px 8px 3px 0"><strong>${p.route ?? '–'}</strong></td>
                          <td style="padding:3px 0;color:${getCategoryColor(p.prediction)};font-weight:500">${p.prediction ?? '–'}</td>
                        </tr>`).join('')
                    : '<tr><td colspan="2" style="color:#64748b;padding:4px 0">No predictions</td></tr>';

                const dotStyle = `display:inline-block;width:8px;height:8px;border-radius:50%;background:${colorInfo.fill};margin-right:6px;vertical-align:middle`;

                const popupContent = `
                  <div style="min-width:220px;font-family:system-ui,sans-serif;font-size:13px;color:#e2e8f0;line-height:1.5">
                    <div style="display:flex;align-items:center;gap:8px;margin-bottom:8px;padding-bottom:8px;border-bottom:1px solid #1e293b">
                      <span style="${dotStyle}"></span>
                      <span style="font-size:15px;font-weight:600;color:#f1f5f9">${airport.name}</span>
                      <span style="margin-left:auto;font-size:11px;color:#475569;background:#1e293b;padding:2px 6px;border-radius:4px">${icao}</span>
                    </div>
                    <div style="color:#94a3b8;margin-bottom:8px;font-size:12px">
                      <span style="margin-right:12px">🌡 ${weatherInfo}</span>
                    </div>
                    <div style="margin-bottom:10px;font-size:12px">
                      <span style="color:#ef4444;margin-right:10px">● ${severe} severe</span>
                      <span style="color:#f97316;margin-right:10px">● ${moderate} moderate</span>
                      <span style="color:#eab308">● ${low} low</span>
                    </div>
                    <table style="width:100%;border-collapse:collapse;font-size:12px">
                      <thead>
                        <tr style="color:#475569;border-bottom:1px solid #1e293b">
                          <th style="text-align:left;padding-bottom:4px;font-weight:500">Route</th>
                          <th style="text-align:left;padding-bottom:4px;font-weight:500">Prediction</th>
                        </tr>
                      </thead>
                      <tbody>${predRows}</tbody>
                    </table>
                  </div>`;

                marker.bindPopup(popupContent, {
                    maxWidth:    280,
                    className:   'dark-popup'
                });
            });

        } catch (error) {
            console.error('Error loading map data:', error);
        }
    }

    document.addEventListener('DOMContentLoaded', initMap);

})();
