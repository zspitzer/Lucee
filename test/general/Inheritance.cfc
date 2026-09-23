component extends="org.lucee.cfml.test.LuceeTestCase" {

	// Generic CFC inheritance contracts:
	//   - super.method() through 3 levels
	//   - `this` resolves to the calling instance from inherited methods
	//   - property pulldown via accessors at every level (mappedSuperClass shape)
	//   - exception thrown from a base UDF references the calling instance
	//   - concurrent createObject + concurrent super.X() under stress

	function run( testResults, testBox ){
		describe( "CFC inheritance", function(){

			describe( "3-level super dispatch", function(){
				it( title="super.super chain returns each level joined", body=function( currentSpec ){
					var c = new inheritance.Child();
					expect( c.chain() ).toBe( "grand:parent:child" );
				});
				it( title="duplicate of a 3-level child preserves super chain", body=function( currentSpec ){
					var c = new inheritance.Child();
					var d = duplicate( c );
					expect( d.chain() ).toBe( "grand:parent:child" );
				});
				it( title="50 sequential new Child() — every chain() resolves identically", body=function( currentSpec ){
					for ( var i=1; i<=50; i++ ) {
						var c = new inheritance.Child();
						expect( c.chain() ).toBe( "grand:parent:child" );
					}
				});
			});

			describe( "`this` inside inherited method resolves to calling child", function(){
				it( title="whoAmI() defined on Grandparent reads child's tag", body=function( currentSpec ){
					var c = new inheritance.Child( tag="alpha" );
					expect( c.whoAmI() ).toBe( "alpha" );
				});
				it( title="two siblings of same class — each whoAmI() returns own tag", body=function( currentSpec ){
					var a = new inheritance.Child( tag="aa" );
					var b = new inheritance.Child( tag="bb" );
					expect( a.whoAmI() ).toBe( "aa" );
					expect( b.whoAmI() ).toBe( "bb" );
				});
				it( title="duplicate then mutate tag — whoAmI() reflects the duplicate's tag", body=function( currentSpec ){
					var orig = new inheritance.Child( tag="orig" );
					var dup = duplicate( orig );
					dup.tag = "dup";
					expect( orig.whoAmI() ).toBe( "orig" );
					expect( dup.whoAmI() ).toBe( "dup" );
				});
				it( title="tagMe() inherited from Grandparent writes to calling child's scope", body=function( currentSpec ){
					var a = new inheritance.Child( tag="a-init" );
					var b = new inheritance.Child( tag="b-init" );
					a.tagMe( "a-after" );
					expect( a.whoAmI() ).toBe( "a-after" );
					expect( b.whoAmI() ).toBe( "b-init" );
				});
			});

			describe( "exception thrown from base UDF references calling instance", function(){
				it( title="boom() inherited from Boom — error message includes Child's tag", body=function( currentSpec ){
					var c = new inheritance.BoomChild( tag="explode" );
					var caught = "";
					var caughtType = "";
					try {
						c.trigger();
					} catch ( any e ) {
						caught = e.message;
						caughtType = e.type;
					}
					expect( caughtType ).toBe( "LDEV6300.Boom" );
					expect( caught ).toBe( "boom from explode" );
				});
				it( title="two siblings throwing — each sees its own tag in the error", body=function( currentSpec ){
					var a = new inheritance.BoomChild( tag="aa" );
					var b = new inheritance.BoomChild( tag="bb" );
					var msgA = "";
					var msgB = "";
					try { a.trigger(); } catch ( any e ) { msgA = e.message; }
					try { b.trigger(); } catch ( any e ) { msgB = e.message; }
					expect( msgA ).toBe( "boom from aa" );
					expect( msgB ).toBe( "boom from bb" );
				});
			});

			describe( "multi-level property pulldown (3-level extends, mappedSuperClass shape)", function(){
				it( title="3-level extends — accessors at every level dispatch on the CFC instance", body=function( currentSpec ){
					var c = new inheritance.Child();
					expect( c.getGprop() ).toBe( "g-default" );
					expect( c.getPprop() ).toBe( "p-default" );
					expect( c.getCprop() ).toBe( "c-default" );
					c.setGprop( "g-set" );
					c.setPprop( "p-set" );
					c.setCprop( "c-set" );
					expect( c.getGprop() ).toBe( "g-set" );
					expect( c.getPprop() ).toBe( "p-set" );
					expect( c.getCprop() ).toBe( "c-set" );
				});
				it( title="duplicate of the CFC instance — accessors at every level still dispatch", body=function( currentSpec ){
					var c = new inheritance.Child();
					c.setGprop( "g1" );
					c.setPprop( "p1" );
					c.setCprop( "c1" );
					var d = duplicate( c );
					expect( d.getGprop() ).toBe( "g1" );
					expect( d.getPprop() ).toBe( "p1" );
					expect( d.getCprop() ).toBe( "c1" );
					d.setGprop( "g2" );
					d.setPprop( "p2" );
					d.setCprop( "c2" );
					expect( c.getGprop() ).toBe( "g1" );
					expect( c.getPprop() ).toBe( "p1" );
					expect( c.getCprop() ).toBe( "c1" );
				});
			});

			describe( "concurrent createObject of same class (init-side stress)", function(){
				it( title="50 threads × 20 fresh Child() — chain + whoAmI consistent per thread", body=function( currentSpec ){
					var threadCount = 50;
					var perThread = 20;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-init-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t perThread=perThread {
							thread.results = [];
							for ( var i=1; i<=attributes.perThread; i++ ) {
								var tag = "t" & attributes.tid & "-i" & i;
								var c = new inheritance.Child( tag=tag );
								arrayAppend( thread.results, { chain: c.chain(), who: c.whoAmI(), expectedTag: tag } );
							}
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-init-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( arrayLen( th.results ) ).toBe( perThread );
						for ( var r in th.results ) {
							expect( r.chain ).toBe( "grand:parent:child" );
							expect( r.who ).toBe( r.expectedTag );
						}
					}
				});
				it( title="concurrent new while siblings mutate `this` scope — no cross-thread bleed", body=function( currentSpec ){
					var threadCount = 30;
					var perThread = 10;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-scope-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t perThread=perThread {
							thread.results = [];
							for ( var i=1; i<=attributes.perThread; i++ ) {
								var c = new inheritance.Child( tag="t" & attributes.tid & "-" & i );
								c.variables.adhoc = "av-" & attributes.tid & "-" & i;
								c.adhocThis = "at-" & attributes.tid & "-" & i;
								arrayAppend( thread.results, {
									who: c.whoAmI(),
									av: c.variables.adhoc,
									at: c.adhocThis,
									expectedTag: "t" & attributes.tid & "-" & i,
									expectedAv: "av-" & attributes.tid & "-" & i,
									expectedAt: "at-" & attributes.tid & "-" & i
								} );
							}
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-scope-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( arrayLen( th.results ) ).toBe( perThread );
						for ( var r in th.results ) {
							expect( r.who ).toBe( r.expectedTag );
							expect( r.av ).toBe( r.expectedAv );
							expect( r.at ).toBe( r.expectedAt );
						}
					}
				});
			});

			describe( "getMetaData reads from inherited dispatch", function(){
				// Grandparent.metaSnapshot() calls getMetaData(this) — when dispatched on
				// a Child, every field below must read the calling LEAF's properties
				// (e.g. "Child"), not Grandparent's.
				it( title="metaSnapshot dispatched from Grandparent reads Child's metadata", body=function( currentSpec ){
					var c = new inheritance.Child();
					var snap = c.metaSnapshot();
					expect( right( snap.name, len( ".inheritance.Child" ) ) ).toBe( ".inheritance.Child" );
					expect( right( snap.fullname, len( ".inheritance.Child" ) ) ).toBe( ".inheritance.Child" );
					expect( right( snap.extends, len( ".inheritance.Parent" ) ) ).toBe( ".inheritance.Parent" );
					expect( snap.accessors ).toBeTrue();
				});
				it( title="metaSnapshot after duplicate still reads Child's metadata", body=function( currentSpec ){
					var c = new inheritance.Child();
					var d = duplicate( c );
					var snap = d.metaSnapshot();
					expect( right( snap.name, len( ".inheritance.Child" ) ) ).toBe( ".inheritance.Child" );
					expect( right( snap.extends, len( ".inheritance.Parent" ) ) ).toBe( ".inheritance.Parent" );
					expect( snap.accessors ).toBeTrue();
				});
				it( title="getMetaData on the CFC instance surfaces inherited properties from every level", body=function( currentSpec ){
					var c = new inheritance.Child();
					var meta = getMetaData( c );
					var propNames = [];
					for ( var p in meta.properties ) arrayAppend( propNames, p.name );
					expect( propNames ).toInclude( "cprop" );
					var allNames = propNames;
					var cur = meta;
					while ( structKeyExists( cur, "extends" ) ) {
						cur = cur.extends;
						if ( structKeyExists( cur, "properties" ) ) {
							for ( var p in cur.properties ) arrayAppend( allNames, p.name );
						}
					}
					expect( allNames ).toInclude( "gprop" );
					expect( allNames ).toInclude( "pprop" );
					expect( allNames ).toInclude( "cprop" );
				});
				it( title="fresh new vs duplicate of new — JSON-serialised metadata is identical", body=function( currentSpec ){
					var orig = new inheritance.Child();
					var dup = duplicate( orig );
					expect( serializeJSON( getMetaData( orig ) ) ).toBe( serializeJSON( getMetaData( dup ) ) );
				});
			});

			describe( "child inherits parent accessors", function(){
				it( title="child inherits parent property accessors", body=function( currentSpec ){
					var child = new inheritance.testInheritChild();
					expect(child.getParentProp()).toBe("from parent");
				});
				it( title="child manual method overrides parent accessor", body=function( currentSpec ){
					var child = new inheritance.testInheritChildOverride();
					expect(child.getParentProp()).toBe("CHILD OVERRIDE");
				});
				it( title="duplicate() with inheritance — child accessors work", body=function( currentSpec ){
					var child = new inheritance.testInheritChild();
					var copy = duplicate( child );
					expect( copy.getParentProp() ).toBe( "from parent" );
					copy.setParentProp( "modified" );
					expect( copy.getParentProp() ).toBe( "modified" );
					expect( child.getParentProp() ).toBe( "from parent" );
				});
				it( title="duplicate() with child override — override preserved", body=function( currentSpec ){
					var orig = new inheritance.testInheritChildOverride();
					var copy = duplicate( orig );
					expect( copy.getParentProp() ).toBe( "CHILD OVERRIDE" );
				});
			});

			describe( "concurrent super dispatch", function(){
				it( title="100 threads × super.chain() — every result is `grand:parent:child`", body=function( currentSpec ){
					var threadCount = 100;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-superrace-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t {
							var c = new inheritance.Child( tag="t-" & attributes.tid );
							thread.chain = c.chain();
							thread.tag = c.tag;
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-superrace-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( th.chain ).toBe( "grand:parent:child" );
						expect( th.tag ).toBe( "t-" & t );
					}
				});
			});

			describe( "5-level chain (Level1 → Level5), synchronized declared on the leaf", function(){
				it( title="super chain resolves through five levels", body=function( currentSpec ){
					expect( new inheritance.Level5().chain() ).toBe( "l1:l2:l3:l4:l5" );
					expect( new inheritance.Level5Open().chain() ).toBe( "l1:l2:l3:l4:l5" );
					expect( new inheritance.Level5().depth() ).toBe( 5 );
				});
				it( title="`this` from a Level1 method resolves to the Level5 instance", body=function( currentSpec ){
					var a = new inheritance.Level5( tag="a" );
					var b = new inheritance.Level5( tag="b" );
					expect( a.whoAmI() ).toBe( "a" );
					expect( b.whoAmI() ).toBe( "b" );
					a.tagMe( "a2" );
					expect( a.tag ).toBe( "a2" );
					expect( b.tag ).toBe( "b" );
				});
				it( title="accessors at every one of the five levels dispatch on the leaf instance", body=function( currentSpec ){
					var l5 = new inheritance.Level5();
					expect( l5.getL1prop() ).toBe( "l1-default" );
					expect( l5.getL2prop() ).toBe( "l2-default" );
					expect( l5.getL3prop() ).toBe( "l3-default" );
					expect( l5.getL4prop() ).toBe( "l4-default" );
					expect( l5.getL5prop() ).toBe( "l5-default" );
					l5.setL1prop( "x1" );
					l5.setL3prop( "x3" );
					expect( l5.getL1prop() ).toBe( "x1" );
					expect( l5.getL3prop() ).toBe( "x3" );
				});
				it( title="getMetaData nests extends four deep down to Level1", body=function( currentSpec ){
					var meta = getMetaData( new inheritance.Level5() );
					expect( meta.name ).toInclude( "Level5" );
					expect( meta.extends.name ).toInclude( "Level4" );
					expect( meta.extends.extends.name ).toInclude( "Level3" );
					expect( meta.extends.extends.extends.name ).toInclude( "Level2" );
					expect( meta.extends.extends.extends.extends.name ).toInclude( "Level1" );
					expect( meta.synchronized ).toBeTrue();
					expect( meta.extends.synchronized ).toBeFalse();
				});
				it( title="duplicate of a Level5 keeps the chain and its own tag", body=function( currentSpec ){
					var orig = new inheritance.Level5( tag="orig" );
					var dup = duplicate( orig );
					dup.tagMe( "dup" );
					expect( dup.chain() ).toBe( "l1:l2:l3:l4:l5" );
					expect( dup.whoAmI() ).toBe( "dup" );
					expect( orig.whoAmI() ).toBe( "orig" );
					expect( getMetaData( dup ).synchronized ).toBeTrue();
				});
				it( title="100 threads × new Level5Open() — chain and `this` correct per thread", body=function( currentSpec ){
					var threadCount = 100;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-l5race-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t {
							var c = new inheritance.Level5Open( tag="t-" & attributes.tid );
							thread.chain = c.chain();
							thread.who = c.whoAmI();
							thread.depth = c.depth();
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-l5race-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( th.chain ).toBe( "l1:l2:l3:l4:l5" );
						expect( th.who ).toBe( "t-" & t );
						expect( th.depth ).toBe( 5 );
					}
				});
				it( title="50 threads × new Level5() (synchronized leaf) — chain, `this` and a bump each", body=function( currentSpec ){
					var threadCount = 50;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-l5sync-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t {
							var c = new inheritance.Level5( tag="s-" & attributes.tid );
							thread.chain = c.chain();
							thread.who = c.whoAmI();
							thread.count = c.bumpChain();
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-l5sync-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( th.chain ).toBe( "l1:l2:l3:l4:l5" );
						expect( th.who ).toBe( "s-" & t );
						expect( th.count ).toBe( 1 );
					}
				});
				it( title="20 threads × bumpChain() on one synchronized Level5 — the super chain down to Level1.bump() runs serialised", body=function( currentSpec ){
					// the lock is the leaf class's flag taken on the leaf instance; the four super hops inside
					// stay under it, so the racy counter in Level1 loses nothing
					request.l5shared = new inheritance.Level5( tag="shared" );
					var threadCount = 20;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-l5shared-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" {
							request.l5shared.bumpChain();
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					expect( request.l5shared.callCount ).toBe( threadCount );
				});
			});

			describe( "structClear on the CFC instance — sibling isolation", function(){
				// structClear( cfc ) empties the instance's own member map: this-scope values and the functions
				// registered on the instance. structClear( variables ) inside the CFC empties the variables shadow only.
				// either way the sibling, a duplicate taken earlier and a fresh instance keep everything.
				it( title="structClear( a ) removes a's this-scope state and functions, b keeps both", body=function( currentSpec ){
					var a = new inheritance.ClearChild( tag="a" );
					var b = new inheritance.ClearChild( tag="b" );
					structClear( a );
					expect( structKeyExists( a, "tag" ) ).toBeFalse();
					expect( structKeyExists( a, "chain" ) ).toBeFalse();
					expect( function(){ a.chain(); } ).toThrow();
					expect( b.tag ).toBe( "b" );
					expect( b.chain() ).toBe( "grand:parent:clear" );
					expect( b.whoAmI() ).toBe( "b" );
					expect( b.getCprop() ).toBe( "c-default" );
					expect( b.getGprop() ).toBe( "g-default" );
				});
				it( title="structClear( this ) inside a's method has the same shape", body=function( currentSpec ){
					var a = new inheritance.ClearChild( tag="a" );
					var b = new inheritance.ClearChild( tag="b" );
					a.clearThis();
					expect( structKeyExists( a, "tag" ) ).toBeFalse();
					expect( function(){ a.chain(); } ).toThrow();
					expect( b.chain() ).toBe( "grand:parent:clear" );
					expect( b.tag ).toBe( "b" );
				});
				it( title="structClear( variables ) inside a empties a's variables only, b and a's this scope are untouched", body=function( currentSpec ){
					var a = new inheritance.ClearChild( tag="a" );
					var b = new inheritance.ClearChild( tag="b" );
					b.setCprop( "b-set" );
					a.clearVariables();
					expect( a.hasInVariables( "cprop" ) ).toBeFalse();
					// LDEV-6300: the accessor is class level and read through on a shadow miss, structClear cannot remove it
					expect( a.hasInVariables( "getCprop" ) ).toBeTrue();
					expect( a.hasInThis( "chain" ) ).toBeTrue();
					expect( a.chain() ).toBe( "grand:parent:clear" );
					expect( a.tag ).toBe( "a" );
					expect( b.hasInVariables( "cprop" ) ).toBeTrue();
					expect( b.hasInVariables( "getCprop" ) ).toBeTrue();
					expect( b.getCprop() ).toBe( "b-set" );
					expect( b.getPprop() ).toBe( "p-default" );
				});
				it( title="a fresh instance created after a sibling was cleared has full state", body=function( currentSpec ){
					var a = new inheritance.ClearChild( tag="a" );
					structClear( a );
					a = new inheritance.ClearChild( tag="a" );
					a.clearVariables();
					var c = new inheritance.ClearChild( tag="c" );
					expect( c.tag ).toBe( "c" );
					expect( c.chain() ).toBe( "grand:parent:clear" );
					expect( c.getCprop() ).toBe( "c-default" );
					expect( c.hasInVariables( "getCprop" ) ).toBeTrue();
				});
				it( title="a duplicate taken before the clear is untouched by it", body=function( currentSpec ){
					var a = new inheritance.ClearChild( tag="a" );
					a.setCprop( "before" );
					var dup = duplicate( a );
					// variables first: once this is cleared the instance has no functions left to call
					a.clearVariables();
					structClear( a );
					expect( dup.tag ).toBe( "a" );
					expect( dup.chain() ).toBe( "grand:parent:clear" );
					expect( dup.getCprop() ).toBe( "before" );
					expect( dup.hasInVariables( "getCprop" ) ).toBeTrue();
				});
				it( title="20 threads each clearing their own instance — no cross-thread bleed", body=function( currentSpec ){
					var threadCount = 20;
					var threadNames = [];
					request.clearKeep = new inheritance.ClearChild( tag="keep" );
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-clear-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t {
							var mine = new inheritance.ClearChild( tag="m-" & attributes.tid );
							structClear( mine );
							thread.cleared = !structKeyExists( mine, "tag" );
							thread.keepChain = request.clearKeep.chain();
							thread.keepTag = request.clearKeep.tag;
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "inheritance-clear-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( th.cleared ).toBeTrue();
						expect( th.keepChain ).toBe( "grand:parent:clear" );
						expect( th.keepTag ).toBe( "keep" );
					}
					expect( request.clearKeep.chain() ).toBe( "grand:parent:clear" );
				});
			});

		});
	}
}
