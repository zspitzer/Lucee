component accessors="true" {
	// Deliberately identical to testPropertyTypes.cfc. Exists so a spec can compare two
	// accessors whose only difference is the CFC they were declared in.
	property name="age" type="numeric" default="25";
	property name="active" type="boolean" default="true";
	property name="name" type="string" default="John";
}
