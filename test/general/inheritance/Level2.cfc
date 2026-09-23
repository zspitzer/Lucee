component extends="Level1" accessors="true" {

	property name="l2prop" default="l2-default";

	function chain() {
		return super.chain() & ":l2";
	}

	function bumpChain( numeric n = 1 ) {
		return super.bumpChain( arguments.n );
	}

	function depth() {
		return super.depth() + 1;
	}

}
