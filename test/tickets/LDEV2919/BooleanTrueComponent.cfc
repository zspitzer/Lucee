// localmode="true" should behave as "modern"
component localmode="true" {

	function testLocalScope() {
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

}
