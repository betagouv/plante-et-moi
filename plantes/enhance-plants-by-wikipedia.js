var fs = require('fs');
var Baby = require('babyparse');

var imageFilters = [".svg", ".png", "Asteracea poster", ,"Sunflowers.JPG", "Fenugrec.jpg", "Akenes cosmos.JPG", "Arcimboldo Summer 1563.jpg", "Graines sauge.jpg", "Fenugreek seeds", "Blsanka Libesovice.jpg", "Achterkant lavendel-oliepers.jpg", "DSC 6546-MR.jpg", "Hommels op prei.jpg", "Laurustinus (Viburnum tinus) fruits close-up (15726127869).jpg"];

const httpGetContent = function(url) {
  return new Promise((resolve, reject) => {
    const lib = url.startsWith('https') ? require('https') : require('http');
    const request = lib.get(url, (response) => {
      response.setEncoding('utf8');
      if (response.statusCode < 200 || response.statusCode > 299) {
         reject(new Error('Failed to load page, status code: ' + response.statusCode));
       }
      const body = [];
      response.on('data', (chunk) => body.push(chunk));
      response.on('end', () => {
          json = JSON.parse(body.join(''));
          resolve(json);
      });
    });
    request.on('error', (err) => reject(err))
    })
};



function getSearchWikipedia(search, lang) {
    return httpGetContent("https://"+lang+".wikipedia.org/w/api.php?action=query&list=search&srsearch="+encodeURIComponent(search)+"&format=json")
}

function getWikipediaPage(page_name, lang) {
    return httpGetContent("https://"+lang+".wikipedia.org/w/api.php?action=query&titles="+encodeURIComponent(page_name)+"&prop=images|info&inprop=url&format=json&imlimit=50")
}

function getWikipediaFile(file_name, lang) {
    return httpGetContent("https://"+lang+".wikipedia.org/w/api.php?action=query&titles="+encodeURIComponent(file_name)+"&prop=imageinfo&iiprop=url|user&iiurlwidth=300&format=json")
}

fs.readFile('data/urban-plants.csv', 'utf8', (err,data) => {
  if (err) {
    return console.log(err);
  }
  var parsed = Baby.parse(data, { header: true, skipEmptyLines: false });
  console.log("Number of plants in csv: "+parsed.data.length);
  var plants_with_page_title = Promise.all(parsed.data.map((plant) => {
    var searchName = plant['Genre'];
    if(plant['Espèce'] != "sp.") {
        searchName += " "+plant['Espèce'];
    } else {
        searchName += " genre";
    }
    return getSearchWikipedia(searchName, "fr")
    .then((result) => { 
        var page = result.query.search[0];
        if(page == undefined || page.title.indexOf("Écozone paléarctique : plantes à graines par nom scientifique") != -1) {
            return getSearchWikipedia(searchName, "en")
                .then((result) => {
                    return {result: result, lang: "en"}
                })
        }
        return new Promise((resolve, reject) => {
            resolve({result: result, lang: "fr"});
        });
    })
    .then((result) => { 
       var page = result.result.query.search[0];
       if(page != undefined) {
          plant["Wikipedia Page Title"] = page.title;
          plant["Wikipedia Lang"] = result.lang;
       }
       return plant;
    });
  }));

  
  var plants_with_image_title = plants_with_page_title.then((results) => 
     Promise.all(results.map((plant) => {
        if(plant["Wikipedia Page Title"] != undefined) {
            return getWikipediaPage(plant["Wikipedia Page Title"], plant["Wikipedia Lang"])
              .then((result) => { 
                var page = Object.values(result.query.pages)[0];
                if(page != undefined) {
                    plant["Wikipedia Page Url"] = page.fullurl;
                    var image = page.images.filter(function(el) { 
                       return  imageFilters.reduce(function (acc, imageFilter) {
						return acc && el.title.indexOf(imageFilter) == -1;
					}, true)})[0];
                    if(image != undefined) {
                        plant["Wikipedia Image Title"] = image.title;
                    }
                } 
                return plant;
              })
        }
        return new Promise((resolve, reject) => {
            resolve(plant);
        })
     }))
  );
  var plants = plants_with_image_title.then((results) => 
     Promise.all(results.map((plant) => {
        if(plant["Wikipedia Image Title"] != undefined) {
            return getWikipediaFile(plant["Wikipedia Image Title"], plant["Wikipedia Lang"])
              .then((result) => { 
                var page = Object.values(result.query.pages)[0];
                if(page != undefined) {
                    plant["Wikipedia Thumb Url"] = page.imageinfo[0].thumburl;
                    plant["Wikipedia Url Url"] = page.imageinfo[0].url;
                }
                return plant;
              })
        }
        return new Promise((resolve, reject) => {
            resolve(plant);
        })
     }))
  );

  plants.then((results) => {  
         console.log("Number of plants to write: "+results.length);
         fs.writeFile("data/enhance-urban-plants.json", JSON.stringify(results, null, 2), function(err) {
            if(err) {
                return console.log(err);
            }
            console.log("The file was saved!");
        });
    })
    .catch((err) => console.error(err));
});

