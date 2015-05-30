
var express = require('express');
var app = express();
var url = require('url');

var http = require('http').Server(app);
fs = require('fs');
var io = require('socket.io')(http);
var     lazy    = require("lazy")


app.use(express.static('stockprediction'));


app.get('/', function(req, res){
res.sendfile(__dirname + '/index.html');




//res.send('<h1>Hello world</h1>');
});


app.get('/myaccount', function(req, res){
res.sendfile(__dirname + '/myaccount.html');




//res.send('<h1>Hello world</h1>');
});

app.get('/update',function(req,res){
        var json = [];
	var url_parts = url.parse(req.url, true);
	var query = url_parts.query;
	lsa = query['data'].split("-")
        jsl = [];
         for(j=0;j<3;j++){
                 jsl.push(lsa[j])// + ",";
         }

         json.push(jsl);
io.emit("datastream",JSON.stringify(json));
    res.send(200);	
});


io.on('connection', function(socket){
	console.log("user connected");
//createJson();	
});



function createJson(){

	var json = [];
	new lazy(fs.createReadStream('sparkdata.txt'))
     .lines
     .forEach(function(line){
         ls = line.toString()
	 lsa = ls.split(",")
	 jsl = [];
         for(j=0;j<4;j++){
		if(j==2)
                  jsl.push(Math.random())// + ",";
                else
	 	 jsl.push(lsa[j])// + ",";
         }
         
	 json.push(jsl);

     }
 ).join(function(x){
    console.log(JSON.stringify(json));
   io.emit("datastream",JSON.stringify(json));
//   createJson();
  });
}


http.listen(80, function(){
  console.log('listening on *:80');
});
