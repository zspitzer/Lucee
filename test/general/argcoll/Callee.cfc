component accessors="true" {

	property name="propA" type="string";

	variables.calls = 0;

	function method( a, b, c, d, e ) {
		return arguments;
	}

	function onMissingMethod( missingMethodName, missingMethodArguments ) {
		return arguments.missingMethodArguments;
	}

	function cached( a ) cachedWithin="request" {
		variables.calls++;
		return { a: arguments.a, calls: variables.calls };
	}

}
