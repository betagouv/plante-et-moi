var mapLayer = L.tileLayer('https://api.mapbox.com/styles/v1/mapbox/light-v9/tiles/256/{z}/{x}/{y}?access_token=pk.eyJ1IjoianVsaWVucGxhbnRlIiwiYSI6ImNpdzNiNzAwajAwMGUyem1vMXF5dDhraXQifQ.uARDuDENWMGtj67c36-qoQ', {
            attribution: "© <a href='https://www.mapbox.com/about/maps/'>Mapbox</a> © <a href='http://www.openstreetmap.org/copyright'>OpenStreetMap</a> <strong><a href='https://www.mapbox.com/map-feedback/' target='_blank'>Improve this map</a></strong>",
                minZoom: 10,
                maxZoom: 20
            });
var photoLayer = L.tileLayer('https://wxs.ign.fr/aux4oyb491ew3s7eqahym3ps/wmts/?SERVICE=WMTS&REQUEST=GetTile&VERSION=1.0.0&LAYER=ORTHOIMAGERY.ORTHOPHOTOS&STYLE=normal&TILEMATRIXSET=PM&TILEMATRIX={z}&TILEROW={y}&TILECOL={x}&FORMAT=image%2Fjpeg', {
            attribution: "&copy; <a href='http://www.ign.fr'>IGN</a>",
            minZoom: 10,
            maxZoom: 19
        });
var baseMaps = {
    "Photo aérienne": photoLayer,
    "Carte": mapLayer
};