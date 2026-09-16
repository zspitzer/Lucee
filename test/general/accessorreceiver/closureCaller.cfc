component accessors="true" {
	property name="name" type="string" default="from-property";

	function viaClosure() {
		var gen = function(){
			return getName();
		};
		return gen();
	}

	function viaNestedClosure() {
		var outer = function(){
			var inner = function(){
				return getName();
			};
			return inner();
		};
		return outer();
	}

	function setViaClosure( required string value ) {
		var gen = function(){
			setName( value );
		};
		gen();
		return this;
	}
}
