component {

	function init( string tag = "plain-base" ) {
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

	function holdFor( required numeric ms, required string flagKey ) {
		request[ arguments.flagKey ] = true;
		sleep( arguments.ms );
		return "held";
	}

	function quick() {
		return "quick";
	}

}
