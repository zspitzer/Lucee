component accessors="true" {

	property name="child" type="any";
	property name="items" type="array";
	property name="meta" type="struct";

	function init() {
		variables.child = new Tagged( "child" );
		variables.items = [ new Tagged( "item-1" ), new Tagged( "item-2" ) ];
		variables.meta = { "nested": { "n": 1 } };
		return this;
	}

	public any function childGetterViaVariables() {
		return variables.getChild();
	}

}
