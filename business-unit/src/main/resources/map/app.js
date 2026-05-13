(() => {
    'use strict';

    let map;

    function initMap() {
        map = L.map('map').setView(36.0, -5.0], 5);

        L.tileLayer('https://{s}.basemaps.cartocdn.com/dark_all/{z}/{x}/{y}{r}.png', {
            attribution: '&copy; OpenStreetMap &copy; CARTO'
        }).addTo(map);

        loadMapData();
    }

    function getColor(delay) {
        const hue = Math.max(0, 120 - (delay * 2));
        return `hsl(${hue}, 100%, 50%)`;
    }

    async function loadMapData() {
        try {
            const response = await fetch('/api/map-data');
            if (!response.ok) throw new Error('Network response was not ok');

            const airports = await response.json();

            airports.forEach(ap => {
                const color = getColor(ap.delay);

                const marker = L.circleMarker([ap.lat, ap.lng], {
                    radius: 8 + (ap.delay / 8), // El tamaño crece según el retraso promedio
                    fillColor: color,
                    color: '#fff',
                    weight: 1.5,
                    opacity: 0.8,
                    fillOpacity: 0.7,
                    className: ap.delay > 30 ? 'pulse-effect' : '' // Clase CSS para aeropuertos críticos
                }).addTo(map);

                // Generar filas de la tabla de vuelos recientes dinámicamente
                const tableRows = ap.recentFlights && ap.recentFlights.length > 0
                    ? ap.recentFlights.map(f => `
                        <tr>
                            <td><strong>${f.flight}</strong></td>
                            <td>${f.dest}</td>
                            <td style="color:${getCategoryColor(f.category)}">${f.delay} min</td>
                        </tr>`).join('')
                    : '<tr><td colspan="3">No recent flight data available</td></tr>';

                // Contenido del Popup (English localized)
                const popupContent = `
                    <div class="map-popup-container" style="min-width: 220px">
                        <h3 style="margin:0 0 5px 0; color:${color}; border-bottom: 1px solid #444;">
                            ${ap.name} (${ap.icao})
                        </h3>
                        <p style="margin: 8px 0; font-size: 14px;">
                            <strong>Avg. Delay:</strong> ${ap.delay.toFixed(1)} min
                        </p>
                        <table class="delay-table" style="width:100%; font-size:12px; border-collapse:collapse;">
                            <thead>
                                <tr style="text-align:left; color:#94a3b8; border-bottom:1px solid #333;">
                                    <th>Flight</th>
                                    <th>Dest</th>
                                    <th>Delay</th>
                                </tr>
                            </thead>
                            <tbody>
                                ${tableRows}
                            </tbody>
                        </table>
                    </div>
                `;

                marker.bindPopup(popupContent);
            });

        } catch (error) {
            console.error('Error fetching map data:', error);
        }
    }

    // Helper para colores de categoría en la tabla
    function getCategoryColor(category) {
        const colors = {
            'none': '#10b981',
            'low': '#84cc16',
            'moderate': '#f59e0b',
            'severe': '#f43f5e'
        };
        return colors[category] || '#fff';
    }

    // Ejecutar al cargar el DOM
    document.addEventListener('DOMContentLoaded', initMap);

})();