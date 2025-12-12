component localmode="modern" {

	// Function explicitly overrides component's modern mode with classic
	function testClassicOverride() localmode="classic" {
		y = 99;
		return {
			variablesHasY: structKeyExists( variables, "y" )
		};
	}

}
