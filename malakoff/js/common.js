//Geo
var center;
var codePostaux = [];
var codeInsee;
var contour;

var requestCityGeoData = new XMLHttpRequest();
requestCityGeoData.open('GET', 'https://geo.api.gouv.fr/communes?fields=code,nom,codesPostaux,centre,contour&nom='+city, true);

requestCityGeoData.onload = function() {
    if (requestCityGeoData.status >= 200 && requestCityGeoData.status < 400) {
    // Success!
        var data = JSON.parse(requestCityGeoData.responseText);
        codePostaux = data[0]["codesPostaux"];
        codeInsee = data[0]["code"];
        center = data[0]["centre"]["coordinates"];
        contour = data[0]["contour"];
        try { onCityGeoDataLoaded(); } catch(e){}
    } else {
    // We reached our target server, but it returned an error

    }
};
requestCityGeoData.send();

// Autocomplete
function renderFeature(item, search){
    var coord = item["geometry"]["coordinates"]
    return '<div class="autocomplete-suggestion" data-val="' + item["properties"]["label"] + '" data-lat="'+coord[1]+'" data-lng="'+coord[0]+'">' + item["properties"]["label"] + '</div>';
}

function onSelectAddress(action) {
    return function(event, term, item){
        var coord = [item.getAttribute("data-lat"),item.getAttribute("data-lng")];
        var address = item.getAttribute("data-val");

        action(coord,address);
    }
}

function addressesDataSource(term, response){
    try { requestAddresses.abort(); } catch(e){}
    requestAddresses = new XMLHttpRequest();
    requestAddresses.open('GET', 'https://api-adresse.data.gouv.fr/search/?type=housenumber&q='+term+'&limit=30&citycode='+codeInsee+'&lon='+center[0]+'&lat='+center[1], true);
    requestAddresses.onload = function() {
        if (requestAddresses.status >= 200 && requestAddresses.status < 400) {
        // Success!
        var data = JSON.parse(requestAddresses.responseText);
        var features = data["features"]/*.filter(function(feature){
            if(codePostaux.indexOf(feature["properties"]["postcode"]) == -1) {
                return false;
            }
            return true;
        })*/;
        response(features);
        } else {
        response([]);
        }
    };
    requestAddresses.send();
}
var requestAddresses;