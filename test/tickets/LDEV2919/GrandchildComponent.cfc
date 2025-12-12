// Grandchild - no localmode, inherits through MiddleComponent from ModernComponent
component extends="MiddleComponent" {

	function testLocalScope() {
		// Should inherit modern from grandparent through parent
		x = 42;
		return {
			localValue: x,
			variablesHasX: structKeyExists( variables, "x" )
		};
	}

}
