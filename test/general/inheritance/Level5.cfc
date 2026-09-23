component extends="Level4" accessors="true" synchronized="true" {

	// five-level chain with the lock declared on the leaf: every dispatch on this instance,
	// including the super chain down to Level1.bump(), runs serialised
	property name="l5prop" default="l5-default";

	function init( string tag = "l5" ) {
		this.tag = arguments.tag;
		this.callCount = 0;
		return this;
	}

	function chain() {
		return super.chain() & ":l5";
	}

	function bumpChain( numeric n = 1 ) {
		return super.bumpChain( arguments.n );
	}

	function depth() {
		return super.depth() + 1;
	}

}
