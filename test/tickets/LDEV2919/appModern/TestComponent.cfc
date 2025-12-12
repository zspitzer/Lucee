// No localmode specified - should inherit from Application.cfc (modern)
component {

	function testLocalScope() {
		x = 42;
		return structKeyExists( variables, "x" );
	}

}
