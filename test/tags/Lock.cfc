component extends="org.lucee.cfml.test.LuceeTestCase" {

	// every cflock goes through one JVM-wide lock table (RWKeyLock), keyed by lock name. Entries are
	// created on acquire and removed when the last holder / waiter releases, on the success and the
	// timeout path, so a name that's not in use must not be left in the table

	function run( testResults, testBox ) {

		describe( title = "cflock", body = function() {

			it( title = "exclusive lock on one name stays exclusive under contention", body = function( currentSpec ) {
				var name = "lock-test-shared-" & createUUID();
				var state = { inside: 0, overlaps: 0, count: 0 };
				arrayEach( seq( 1, 16 ), function( t ) {
					for ( var i = 1; i <= 200; i++ ) {
						lock name=name type="exclusive" timeout=10 {
							state.inside++;
							if ( state.inside > 1 ) state.overlaps++;
							state.count++;
							state.inside--;
						}
					}
				}, true, 16 );
				expect( state.overlaps ).toBe( 0 );
				expect( state.count ).toBe( 16 * 200 );
				expectNoEntry( name );
			});

			it( title = "distinct names don't leave entries behind", body = function( currentSpec ) {
				var prefix = "lock-test-distinct-" & createUUID() & "-";
				var errors = [];
				arrayEach( seq( 1, 16 ), function( t ) {
					for ( var i = 1; i <= 200; i++ ) {
						try {
							lock name="#prefix##t#-#i % 5#" type=( i % 2 ? "readonly" : "exclusive" ) timeout=10 {
							}
						}
						catch ( any e ) {
							arrayAppend( errors, e.message );
						}
					}
				}, true, 16 );
				expect( errors ).toBe( [] );
				for ( var t = 1; t <= 16; t++ ) {
					for ( var i = 0; i < 5; i++ ) expectNoEntry( "#prefix##t#-#i#" );
				}
			});

			it( title = "a lock that times out doesn't leave an entry behind", body = function( currentSpec ) {
				var name = "lock-test-timeout-" & createUUID();
				var res = { timedOut: false, type: "" };
				arrayEach( [ "holder", "waiter" ], function( role ) {
					if ( role == "holder" ) {
						lock name=name type="exclusive" timeout=5 {
							sleep( 500 );
						}
					}
					else {
						sleep( 100 );
						try {
							lock name=name type="exclusive" timeout=0.1 {
							}
						}
						catch ( any e ) {
							res.timedOut = true;
							res.type = e.type;
						}
					}
				}, true, 2 );
				expect( res.timedOut ).toBeTrue();
				expect( res.type ).toBe( "lock" );
				expectNoEntry( name );
			});

			it( title = "a lock that times out with throwOnTimeout=false doesn't leave an entry behind", body = function( currentSpec ) {
				var name = "lock-test-nothrow-" & createUUID();
				var res = { ran: false };
				arrayEach( [ "holder", "waiter" ], function( role ) {
					if ( role == "holder" ) {
						lock name=name type="exclusive" timeout=5 {
							sleep( 500 );
						}
					}
					else {
						sleep( 100 );
						lock name=name type="exclusive" timeout=0.1 throwOnTimeout=false {
							res.ran = true;
						}
					}
				}, true, 2 );
				expect( res.ran ).toBeFalse();
				expectNoEntry( name );
			});

			it( title = "readonly locks on one name are shared", body = function( currentSpec ) {
				var name = "lock-test-readonly-" & createUUID();
				var state = { inside: 0, maxInside: 0 };
				arrayEach( seq( 1, 4 ), function( t ) {
					lock name=name type="readonly" timeout=5 {
						lock name="#name#-counter" type="exclusive" timeout=5 {
							state.inside++;
							if ( state.inside > state.maxInside ) state.maxInside = state.inside;
						}
						sleep( 200 );
						lock name="#name#-counter" type="exclusive" timeout=5 {
							state.inside--;
						}
					}
				}, true, 4 );
				expect( state.maxInside ).toBeGT( 1 );
				expectNoEntry( name );
				expectNoEntry( "#name#-counter" );
			});

		});
	}

	private function expectNoEntry( required string name ) {
		var lm = getPageContext().getConfig().getLockManager();
		expect( arrayContains( lm.getOpenLockNames(), lCase( arguments.name ) ) ).toBeFalse( "open lock [#arguments.name#]" );
		// isWriteLocked returns null only when the name has no entry in the table
		expect( isNull( lm.isWriteLocked( arguments.name ) ) ).toBeTrue( "leaked lock entry [#arguments.name#]" );
	}

	private array function seq( required numeric from, required numeric to ) {
		var arr = [];
		for ( var i = arguments.from; i <= arguments.to; i++ ) arrayAppend( arr, i );
		return arr;
	}

}
