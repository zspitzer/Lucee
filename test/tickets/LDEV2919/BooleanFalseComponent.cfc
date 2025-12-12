// localmode="false" should behave as "classic"
component localmode="false" {

	function testLocalScope() {
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

}
