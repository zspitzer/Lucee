component synchronized="true" {

	function init( string tag = "synced-base" ) {
		this.tag = arguments.tag;
		this.callCount = 0;
		return this;
	}

	function bump( numeric n = 1 ) {
		var oldCount = this.callCount;
		sleep( 1 );
		this.callCount = oldCount + arguments.n;
		return this.callCount;
	}

	// enters, flags that it is inside, then sits on the instance for a while.
	// with the lock held, any other dispatch on this instance has to wait for it to leave
	function holdFor( required numeric ms, required string flagKey ) {
		request[ arguments.flagKey ] = true;
		sleep( arguments.ms );
		return "held";
	}

	function quick() {
		return "quick";
	}

}
