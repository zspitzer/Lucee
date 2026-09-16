component extends="parent" accessors="true" {
	property name="childProp" default="from-child";

	function getParentProp() {
		return "from-manual-override";
	}
}
