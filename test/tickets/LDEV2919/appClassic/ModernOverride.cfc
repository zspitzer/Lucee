// Component explicitly sets modern, overriding Application.cfc's classic
component localmode="modern" {

	function testLocalScope() {
		x = 42;
		return structKeyExists( variables, "x" );
	}

}
