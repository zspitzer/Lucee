component extends="PlainBase" synchronized="true" {

	// the inverse of OpenChild: a synchronized leaf over a plain base.
	// every dispatch on this instance takes the lock, including the inherited methods
	function bumpViaSuper( numeric n = 1 ) {
		return super.bump( arguments.n );
	}

}
