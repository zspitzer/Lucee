// Extends ModernComponent but explicitly sets classic - should override parent
component extends="ModernComponent" localmode="classic" {

	function testOverriddenLocalScope() {
		// Should use classic mode despite parent being modern
		w = 456;
		return {
			variablesHasW: structKeyExists( variables, "w" )
		};
	}

}
