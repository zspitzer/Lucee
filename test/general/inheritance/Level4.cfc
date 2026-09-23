component extends="Level3" accessors="true" {

	property name="l4prop" default="l4-default";

	function chain() {
		return super.chain() & ":l4";
	}

	function bumpChain( numeric n = 1 ) {
		return super.bumpChain( arguments.n );
	}

	function depth() {
		return super.depth() + 1;
	}

}
