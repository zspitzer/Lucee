component accessors="true" {
	property name="value" default="outer";

	function testNested() {
		inner = new testWithAccessors();
		inner.setA( "inner value" );
		// Now call our own getter - should return "outer", not "inner value"
		return this.getValue() & ":" & inner.getA();
	}
}
