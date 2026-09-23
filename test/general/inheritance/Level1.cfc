component accessors="true" {

	property name="l1prop" default="l1-default";

	function chain() {
		return "l1";
	}

	function whoAmI() {
		return this.tag;
	}

	function tagMe( required string newTag ) {
		this.tag = arguments.newTag;
	}

	// racy counter, only safe when the dispatch on the leaf instance is serialised
	function bump( numeric n = 1 ) {
		var oldCount = this.callCount;
		sleep( 1 );
		this.callCount = oldCount + arguments.n;
		return this.callCount;
	}

	function bumpChain( numeric n = 1 ) {
		return bump( arguments.n );
	}

	function depth() {
		return 1;
	}

}
