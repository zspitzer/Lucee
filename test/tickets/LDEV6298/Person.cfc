component accessors="true" {
	property name="name" type="string";

	this.plain = "x";
	this.nada = nullValue();

	public function hello() {
		return "hi";
	}

	// probes and a by-name call over the variables scope from inside the component
	public struct function probeScope() {
		return {
			bif: structKeyExists( variables, "getName" ),
			member: variables.keyExists( "getName" ),
			missing: structKeyExists( variables, "getNope" ),
			called: getName()
		};
	}

	public struct function copyScope() {
		return structCopy( variables );
	}
}
