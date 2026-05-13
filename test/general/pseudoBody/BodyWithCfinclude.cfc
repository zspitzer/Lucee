// cfinclude inside the component body. Even though the include itself may resolve to nothing, the
// include shape is a non-function, non-cfproperty, non-cfimport statement → True bucket.
component {

	include template="emptyInclude.cfm";

	function getX() { return "x"; }
}
