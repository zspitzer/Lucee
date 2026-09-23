component extends="Level2" accessors="true" {

	property name="l3prop" default="l3-default";

	function chain() {
		return super.chain() & ":l3";
	}

	function bumpChain( numeric n = 1 ) {
		return super.bumpChain( arguments.n );
	}

	function depth() {
		return super.depth() + 1;
	}

}
