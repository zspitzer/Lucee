component {

	// Root of the 3-level super-dispatch chain: ChainLeaf extends ChainMid extends ChainTop.
	// Each level's trace() prefixes its own name and delegates to super, so the returned
	// string records which level each `super` resolved to.

	function init( string tag = "" ) {
		this.tag = arguments.tag;
		return this;
	}

	function trace() {
		return "top";
	}

	function whoAmI() {
		// Dispatched via super from ChainMid — `this` must resolve to the CFC instance,
		// which is the leaf of whichever chain is executing.
		return this.tag;
	}

}