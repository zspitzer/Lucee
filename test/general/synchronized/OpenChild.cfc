component extends="SyncedBase" {

	// child of a synchronized base that does not redeclare synchronized itself.
	// the guard reads the leaf class's flag, so nothing dispatched on this instance takes the lock,
	// including the inherited methods and the super.x() reach into the base
	function bumpViaSuper( numeric n = 1 ) {
		return super.bump( arguments.n );
	}

	function holdViaSuper( required numeric ms, required string flagKey ) {
		return super.holdFor( arguments.ms, arguments.flagKey );
	}

	function quickViaSuper() {
		return super.quick();
	}

}
