var console = { };
console.error =  function error(){
    var s = '';
	for (var i = 0, len = arguments.length; i < len; i++) {
	    if (i > 0) s = s + ' ';
	    if (typeof arguments[i] === 'undefined') {
            s = s + 'undefined';
	    } else if (typeof arguments[i] === 'object') {
	        s = s + JSON.stringify(arguments[i]);
	    } else {
            s = s + arguments[i].toString();
        }
	}
	s = s + "\n";
	console2.log(s);
};

console.log =  function log(){
    var s = '';
	for (var i = 0, len = arguments.length; i < len; i++) {
	    if (i > 0) s = s + ' ';
	    if (typeof arguments[i] === 'undefined') {
            s = s + 'undefined';
	    } else if (typeof arguments[i] === 'object') {
	        s = s + JSON.stringify(arguments[i]);
	    } else {
            s = s + arguments[i].toString();
        }
		//console2.log(arguments[i]);
	}
	s = s + "\n";
	console2.log(s);
};
