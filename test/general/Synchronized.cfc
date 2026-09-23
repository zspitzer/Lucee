component extends="org.lucee.cfml.test.LuceeTestCase" {

	// `synchronized="true"` wraps method dispatch in `synchronized(this)` at runtime.
	// The flag is set at compile time on the class's ComponentProperties — it is NOT
	// inherited from a parent class at instance-init time. These tests pin both the
	// per-class semantics and the runtime serialisation behaviour under stress.

	function run( testResults, testBox ){
		describe( "synchronized component dispatch", function(){

			it( title="single-thread bump() increments and persists across calls", body=function( currentSpec ){
				var s = new synchronized.Synced();
				expect( s.callCount ).toBe( 0 );
				s.bump();
				expect( s.callCount ).toBe( 1 );
				s.bump();
				s.bump();
				expect( s.callCount ).toBe( 3 );
			});
			it( title="getMetaData reflects synchronized=true on the declaring class", body=function( currentSpec ){
				var s = new synchronized.Synced();
				expect( getMetaData( s ).synchronized ).toBeTrue();
			});
			it( title="getMetaData on a child that doesn't redeclare synchronized — child class itself is NOT synchronized", body=function( currentSpec ){
				// Per-class semantics: SyncedChild extends Synced but doesn't declare
				// synchronized="true" itself, so its own metadata reports synchronized=false.
				// The flag IS visible on the parent via getMetaData(child).extends.synchronized.
				var sc = new synchronized.SyncedChild();
				expect( getMetaData( sc ).synchronized ).toBeFalse();
				expect( getMetaData( sc ).extends.synchronized ).toBeTrue();
			});
			it( title="synchronized parent — concurrent bumps on shared instance don't lose updates", body=function( currentSpec ){
				request.syncTarget = new synchronized.Synced();
				var threadCount = 20;
				var threadNames = [];
				for ( var t=1; t<=threadCount; t++ ) {
					var tname = "synchronized-sync-" & t;
					arrayAppend( threadNames, tname );
					thread name="#tname#" {
						request.syncTarget.bump();
					}
				}
				thread action="join" name="#arrayToList( threadNames )#";
				expect( request.syncTarget.callCount ).toBe( threadCount );
			});
			it( title="duplicate of synchronized parent — duplicate retains the synchronized flag", body=function( currentSpec ){
				var s = new synchronized.Synced();
				request.syncDup = duplicate( s );
				expect( getMetaData( request.syncDup ).synchronized ).toBeTrue();
				var threadCount = 20;
				var threadNames = [];
				for ( var t=1; t<=threadCount; t++ ) {
					var tname = "synchronized-syncdup-" & t;
					arrayAppend( threadNames, tname );
					thread name="#tname#" {
						request.syncDup.bump();
					}
				}
				thread action="join" name="#arrayToList( threadNames )#";
				expect( request.syncDup.callCount ).toBe( threadCount );
				expect( s.callCount ).toBe( 0 );
			});

		});

		describe( "which class's synchronized flag the guard reads under inheritance", function(){
			// the lock is taken per dispatch on the leaf instance, and the flag consulted is the leaf class's.
			// so a plain child of a synchronized base runs unlocked, and a synchronized leaf over a plain base
			// runs locked, for inherited methods and for the super.x() reach alike.
			//
			// probe: one thread enters holdFor() and sits on the instance; once it has flagged that it is inside,
			// the main thread times a call to quick() on the same instance. locked: quick waits for the hold.
			// unlocked: quick returns straight away.
			var holdMs = 400;
			var lockedFloorMs = 200;
			var unlockedCeilingMs = 100;

			var timeQuickDuringHold = function( required any target, required string holdMethod, required string quickMethod, required string label ) {
				var flagKey = "syncprobe-" & arguments.label & "-" & createUUID();
				var tname = "syncprobe-hold-" & arguments.label;
				request[ "syncprobe-target-" & arguments.label ] = arguments.target;
				thread name="#tname#" holdMethod=arguments.holdMethod flagKey=flagKey label=arguments.label holdMs=holdMs {
					var t = request[ "syncprobe-target-" & attributes.label ];
					thread.held = invoke( t, attributes.holdMethod, { ms: attributes.holdMs, flagKey: attributes.flagKey } );
				}
				var waited = 0;
				while ( !structKeyExists( request, flagKey ) && waited < 2000 ) {
					sleep( 5 );
					waited += 5;
				}
				if ( !structKeyExists( request, flagKey ) ) throw( "hold thread never entered holdFor()" );
				var t0 = getTickCount();
				var r = invoke( arguments.target, arguments.quickMethod );
				var elapsed = getTickCount() - t0;
				thread action="join" name="#tname#";
				var th = cfthread[ tname ];
				if ( th.status != "COMPLETED" ) throw( object=th.error );
				expect( r ).toBe( "quick" );
				expect( th.held ).toBe( "held" );
				return elapsed;
			};

			it( title="synchronized base: quick() waits for a hold on the same instance", body=function( currentSpec ){
				var elapsed = timeQuickDuringHold( new synchronized.SyncedBase(), "holdFor", "quick", "base" );
				expect( elapsed ).toBeGTE( lockedFloorMs );
			});
			it( title="synchronized base: a hold on one instance does not block quick() on a sibling", body=function( currentSpec ){
				var a = new synchronized.SyncedBase( "a" );
				var b = new synchronized.SyncedBase( "b" );
				var flagKey = "syncprobe-sibling-" & createUUID();
				request[ "syncprobe-sibling-a" ] = a;
				thread name="syncprobe-sibling-hold" flagKey=flagKey holdMs=holdMs {
					thread.held = request[ "syncprobe-sibling-a" ].holdFor( attributes.holdMs, attributes.flagKey );
				}
				var waited = 0;
				while ( !structKeyExists( request, flagKey ) && waited < 2000 ) { sleep( 5 ); waited += 5; }
				var t0 = getTickCount();
				expect( b.quick() ).toBe( "quick" );
				var elapsed = getTickCount() - t0;
				thread action="join" name="syncprobe-sibling-hold";
				expect( elapsed ).toBeLT( unlockedCeilingMs );
			});
			it( title="plain child of a synchronized base: inherited holdFor()/quick() on the child do NOT serialise", body=function( currentSpec ){
				var elapsed = timeQuickDuringHold( new synchronized.OpenChild(), "holdFor", "quick", "openchild" );
				expect( elapsed ).toBeLT( unlockedCeilingMs );
			});
			it( title="plain child of a synchronized base: super.holdFor()/super.quick() do NOT serialise either", body=function( currentSpec ){
				var elapsed = timeQuickDuringHold( new synchronized.OpenChild(), "holdViaSuper", "quickViaSuper", "openchild-super" );
				expect( elapsed ).toBeLT( unlockedCeilingMs );
			});
			it( title="synchronized leaf over a plain base: inherited holdFor()/quick() on the leaf DO serialise", body=function( currentSpec ){
				var elapsed = timeQuickDuringHold( new synchronized.SyncedLeaf(), "holdFor", "quick", "syncedleaf" );
				expect( elapsed ).toBeGTE( lockedFloorMs );
			});
			it( title="synchronized leaf over a plain base: 20 threads × inherited bump() on one instance lose nothing", body=function( currentSpec ){
				request.syncLeaf = new synchronized.SyncedLeaf();
				var threadCount = 20;
				var threadNames = [];
				for ( var t=1; t<=threadCount; t++ ) {
					var tname = "synchronized-leaf-bump-" & t;
					arrayAppend( threadNames, tname );
					thread name="#tname#" {
						request.syncLeaf.bump();
					}
				}
				thread action="join" name="#arrayToList( threadNames )#";
				expect( request.syncLeaf.callCount ).toBe( threadCount );
			});
			it( title="synchronized leaf over a plain base: 20 threads × super.bump() through the leaf lose nothing", body=function( currentSpec ){
				request.syncLeafSuper = new synchronized.SyncedLeaf();
				var threadCount = 20;
				var threadNames = [];
				for ( var t=1; t<=threadCount; t++ ) {
					var tname = "synchronized-leaf-superbump-" & t;
					arrayAppend( threadNames, tname );
					thread name="#tname#" {
						request.syncLeafSuper.bumpViaSuper();
					}
				}
				thread action="join" name="#arrayToList( threadNames )#";
				expect( request.syncLeafSuper.callCount ).toBe( threadCount );
			});
			it( title="getMetaData: the flag is reported per declaring class in both directions", body=function( currentSpec ){
				var leaf = new synchronized.SyncedLeaf();
				expect( getMetaData( leaf ).synchronized ).toBeTrue();
				expect( getMetaData( leaf ).extends.synchronized ).toBeFalse();
				var open = new synchronized.OpenChild();
				expect( getMetaData( open ).synchronized ).toBeFalse();
				expect( getMetaData( open ).extends.synchronized ).toBeTrue();
			});
		});
	}
}
