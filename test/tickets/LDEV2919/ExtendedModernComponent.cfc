// Extends ModernComponent but doesn't specify localmode - should inherit modern
component extends="ModernComponent" {

	function testInheritedLocalScope() {
		// Should inherit modern mode from parent
		z = 123;
		return {
			variablesHasZ: structKeyExists( variables, "z" )
		};
	}

}
