var console = { };
console.error =  function error(){
	for (var i = 0, len = arguments.length; i < len; i++) {
		console2.log(arguments[i]);
	}
};

console.log =  function log(){
	for (var i = 0, len = arguments.length; i < len; i++) {
		console2.log(arguments[i]);
	}
};
