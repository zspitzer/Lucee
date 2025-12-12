component localmode="classic" {

	function testLocalScope() {
		// Without 'var', in classic mode this should leak to variables scope
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

}
