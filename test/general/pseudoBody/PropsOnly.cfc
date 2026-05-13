// cfproperty + function declarations only. Both shapes are class-level — declarative-only body.
component accessors="true" {

	property name="first"  type="string" default="alice";
	property name="age"    type="numeric" default="42";
	property name="active" type="boolean" default="true";

	function getDisplay() {
		return variables.first & " (" & variables.age & ")";
	}
}
