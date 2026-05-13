// Minimal True case: a single cfset in the body. Should classify True regardless of how trivial.
component {

	variables.created = now();

	function getCreated() { return variables.created; }
}
