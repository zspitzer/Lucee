component localmode="modern" {

	function testLocalScope() {
		// Without 'var', in modern mode this should still be local-scoped
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

	static function testStaticLocalScope() {
		// Static function should also respect component localmode
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( static, "x" )
		};
	}

}
