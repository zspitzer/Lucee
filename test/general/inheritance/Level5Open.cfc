component extends="Level4" accessors="true" {

	// same five-level chain, no lock anywhere on the leaf; the chain string and `this` reads
	// must be right per instance regardless
	property name="l5prop" default="l5open-default";

	function init( string tag = "l5open" ) {
		this.tag = arguments.tag;
		this.callCount = 0;
		return this;
	}

	function chain() {
		return super.chain() & ":l5";
	}

	function depth() {
		return super.depth() + 1;
	}

}
