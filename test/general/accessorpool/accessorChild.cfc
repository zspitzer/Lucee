// accessors off on the base, on in the child: the child must reach the inherited
// accessor through the per instance path, not the base pool
component extends="plainBase" accessors="true" {
	property name="ownProp" default="from-child";
}
