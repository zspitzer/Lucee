// Component without localmode - should inherit from Application.cfc or server default
component {

	function testLocalScope() {
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

}
