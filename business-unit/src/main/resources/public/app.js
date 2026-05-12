let charts = {};

async function init() {
    const response = await fetch('/api/data');
    const data = await response.json();

    updateKPIs(data);
    renderCharts(data);
}

function updateKPIs(data) {
    document.getElementById('kpi-total').innerText = data.length;
    const severe = data.filter(f => f.prediction === 'severe').length;
    document.getElementById('kpi-alerts').innerText = severe;

    const avgWind = data.reduce((acc, curr) => acc + (curr.wind || 0), 0) / data.length;
    document.getElementById('kpi-wind').innerHTML = `${avgWind.toFixed(1)}<small>km/h</small>`;
}

function renderCharts(data) {
    const ctxPie = document.getElementById('pieChart').getContext('2d');
    const counts = {
        'none': data.filter(f => f.prediction === 'none').length,
        'moderate': data.filter(f => f.prediction === 'moderate').length,
        'severe': data.filter(f => f.prediction === 'severe').length
    };

    charts.pie = new Chart(ctxPie, {
        type: 'doughnut',
        data: {
            labels: ['ON TIME', 'MODERATE', 'SEVERE'],
            datasets: [{
                data: [counts.none, counts.moderate, counts.severe],
                backgroundColor: ['#10b981', '#f59e0b', '#f43f5e'],
                borderWidth: 0,
                hoverOffset: 20
            }]
        },
        options: {
            plugins: { legend: { position: 'bottom', labels: { color: '#94a3b8', font: { family: 'Syne' } } } },
            cutout: '70%'
        }
    });

    const airports = [...new Set(data.map(f => f.route.split('->')[0]))].slice(0, 5);
    const airportData = airports.map(a => data.filter(f => f.route.startsWith(a) && f.prediction !== 'none').length);

    const ctxBar = document.getElementById('barChart').getContext('2d');
    charts.bar = new Chart(ctxBar, {
        type: 'bar',
        data: {
            labels: airports,
            datasets: [{
                label: 'Active Alerts',
                data: airportData,
                backgroundColor: '#00d9ff',
                borderRadius: 8
            }]
        },
        options: {
            scales: {
                y: { grid: { color: 'rgba(255,255,255,0.05)' }, ticks: { color: '#94a3b8' } },
                x: { ticks: { color: '#94a3b8' } }
            }
        }
    });

    // Scatter Correlation
    const ctxScatter = document.getElementById('scatterChart').getContext('2d');
    charts.scatter = new Chart(ctxScatter, {
        type: 'scatter',
        data: {
            datasets: [{
                label: 'Weather Impact',
                data: data.map(f => ({ x: f.wind, y: f.humidity })),
                backgroundColor: 'rgba(0, 217, 255, 0.5)'
            }]
        },
        options: {
            scales: {
                x: { title: { display: true, text: 'Wind Speed', color: '#94a3b8' } },
                y: { title: { display: true, text: 'Humidity %', color: '#94a3b8' } }
            }
        }
    });
}

init();

setInterval(init, 10000);