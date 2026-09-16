component extends="org.lucee.cfml.test.LuceeTestCase" labels="java,component" {

	// LDEV-3335 Option 2 — the class level accessor pool is the primary source of accessor UDFs.
	//
	// Every component with properties builds its accessors once, in <clinit>, and initProperties
	// hands the same objects to every instance, so PropertyFactory allocates nothing per new().
	// The pooled accessors carry no owner component — dispatch takes the receiver as a parameter —
	// and nothing on the registration, duplicate or deserialize paths may claim one for an instance.
	//
	// Java reflection is needed for the identity claims: sharing has no CFML surface, and a
	// regression that silently goes back to per-instance allocation breaks no behaviour, only the
	// allocation profile these specs exist to pin.

	function run( testResults, testBox ){
		describe( "LDEV-3335 accessor pool", function(){

			describe( "pool identity", function(){
				it( title="all five accessor kinds are one Java instance across fresh instances", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var B = new accessorpool.pooled();
					var keys = [ "getName", "setName", "getAge", "setAge", "getTags", "setTags", "hasTag", "addTag", "removeTag", "getPassport", "setPassport", "hasPassport" ];
					for ( var key in keys ) {
						var udfA = probeUdf( A, key );
						var udfB = probeUdf( B, key );
						expect( isNull( udfA ) ).toBeFalse( "[#key#] missing on the first instance" );
						expect( isNull( udfB ) ).toBeFalse( "[#key#] missing on the second instance" );
						expect( idOf( udfA ) ).toBe( idOf( udfB ), "[#key#] is not shared between instances" );
						expect( isNull( srcOf( udfA ) ) ).toBeTrue( "[#key#] has an owner component" );
					}
				});

				it( title="a one-to-one property gets has but not add or remove", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					expect( isNull( probeUdf( A, "hasPassport" ) ) ).toBeFalse();
					expect( isNull( probeUdf( A, "addPassport" ) ) ).toBeTrue();
					expect( isNull( probeUdf( A, "removePassport" ) ) ).toBeTrue();
				});

				it( title="a component without accessors gets no pooled accessors", body=function( currentSpec ){
					var cfc = new accessors.testNoAccessors();
					expect( structKeyExists( cfc, "getFirstName" ) ).toBeFalse();
				});
			});

			describe( "dispatch on pooled accessors", function(){
				it( title="fast path reads and writes the receiver, siblings isolated", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var B = new accessorpool.pooled();
					A.setName( "alpha" );
					B.setName( "bravo" );
					expect( A.getName() ).toBe( "alpha" );
					expect( B.getName() ).toBe( "bravo" );
				});

				it( title="setter via reference and via bracket notation hits the receiver only", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var B = new accessorpool.pooled();
					var setName = A.setName;
					setName( "alpha" );
					expect( A.getName() ).toBe( "alpha" );
					expect( isNull( B.getName() ) ).toBeTrue();

					A[ "setName" ]( "delta" );
					expect( A.getName() ).toBe( "delta" );
					expect( isNull( B.getName() ) ).toBeTrue();
				});

				it( title="collection helpers via reference and bracket notation hit the receiver only", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var B = new accessorpool.pooled();
					var addTag = A.addTag;
					addTag( "one" );
					A[ "addTag" ]( "two" );
					expect( arrayLen( A.getTags() ) ).toBe( 2 );
					expect( A.hasTag( "one" ) ).toBeTrue();
					expect( A[ "hasTag" ]( "two" ) ).toBeTrue();
					expect( isNull( B.getTags() ) ).toBeTrue();

					var removeTag = A.removeTag;
					removeTag( "one" );
					expect( A.hasTag( "one" ) ).toBeFalse();
					expect( arrayLen( A.getTags() ) ).toBe( 1 );
				});
			});

			describe( "duplicate of a pooled instance", function(){
				it( title="shares the same accessor objects and dispatches to the copy", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					A.setName( "alpha" );
					A.addTag( "one" );
					var D = duplicate( A );

					expect( idOf( probeUdf( D, "getName" ) ) ).toBe( idOf( probeUdf( A, "getName" ) ) );
					expect( idOf( probeUdf( D, "addTag" ) ) ).toBe( idOf( probeUdf( A, "addTag" ) ) );
					expect( D.getName() ).toBe( "alpha" );

					D.setName( "delta" );
					D.addTag( "two" );
					expect( A.getName() ).toBe( "alpha" );
					expect( arrayLen( A.getTags() ) ).toBe( 1 );
					expect( arrayLen( D.getTags() ) ).toBe( 2 );

					var getName = D.getName;
					expect( getName() ).toBe( "delta" );
				});
			});

			describe( "inheritance", function(){
				it( title="a manual override wins, a sibling child still gets the parent's pooled accessor", body=function( currentSpec ){
					var parent = new accessorpool.parent();
					var plain = new accessorpool.childPlain();
					var override = new accessorpool.childOverride();

					expect( idOf( probeUdf( plain, "getParentProp" ) ) ).toBe( idOf( probeUdf( parent, "getParentProp" ) ) );
					expect( idOf( probeUdf( override, "getParentProp" ) ) ).notToBe( idOf( probeUdf( parent, "getParentProp" ) ) );

					expect( plain.getParentProp() ).toBe( "from-parent" );
					expect( override.getParentProp() ).toBe( "from-manual-override" );
					expect( parent.getParentProp() ).toBe( "from-parent" );
				});

				it( title="a child re-declaring a parent property does not mint a second accessor", body=function( currentSpec ){
					var parent = new accessorpool.parent();
					var child = new accessorpool.childRedeclare();
					expect( idOf( probeUdf( child, "getParentProp" ) ) ).toBe( idOf( probeUdf( parent, "getParentProp" ) ) );
					// the child's own default still wins
					expect( child.getParentProp() ).toBe( "re-declared" );
					expect( parent.getParentProp() ).toBe( "from-parent" );
				});
			});

			describe( "metadata", function(){
				it( title="getMetaData lists own and inherited accessors at the right level", body=function( currentSpec ){
					var child = new accessorpool.childPlain();
					var md = getMetaData( child );

					expect( functionNames( md.functions ) ).toInclude( "getchildprop" );
					expect( functionNames( md.functions ) ).notToInclude( "getparentprop" );
					expect( functionNames( md.extends.functions ) ).toInclude( "getparentprop" );
				});

				it( title="an inherited accessor reports the declaring component as its owner", body=function( currentSpec ){
					var child = new accessorpool.childPlain();
					var md = getMetaData( child );
					for ( var fn in md.extends.functions ) {
						if ( fn.name == "getParentProp" ) {
							expect( fn.owner ).toInclude( "parent.cfc" );
							return;
						}
					}
					fail( "getParentProp not found on the parent metadata" );
				});

				it( title="metadata is identical for a fresh instance and a duplicate", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var D = duplicate( A );
					expect( serializeJSON( getMetaData( D ) ) ).toBe( serializeJSON( getMetaData( A ) ) );
				});
			});

			describe( "write sites that must not claim a pooled accessor", function(){
				it( title="ObjectSave/ObjectLoad round trip leaves the pool unowned and dispatches to the copy", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					A.setName( "alpha" );
					var C = new accessorpool.pooled();
					C.setName( "charlie" );
					var D = ObjectLoad( ObjectSave( C ) );

					expect( D.getName() ).toBe( "charlie" );
					D.setName( "delta" );
					expect( D.getName() ).toBe( "delta" );
					expect( C.getName() ).toBe( "charlie" );
					expect( A.getName() ).toBe( "alpha" );

					var getName = D.getName;
					expect( getName() ).toBe( "delta" );
					expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
					expect( isNull( srcOf( probeUdf( D, "getName" ) ) ) ).toBeTrue();
				});

				it( title="mixing a pooled accessor into another component does not claim it for the host", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					A.setName( "alpha" );
					var host = new accessorpool.mixinHost();

					expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
					// the source class still reports itself as the declaring component
					var md = getMetaData( A );
					for ( var fn in md.functions ) {
						if ( fn.name == "getName" ) {
							expect( fn.owner ).toInclude( "pooled.cfc" );
							break;
						}
					}
					// and the source instances still dispatch to themselves
					var B = new accessorpool.pooled();
					B.setName( "bravo" );
					expect( A.getName() ).toBe( "alpha" );
					expect( B.getName() ).toBe( "bravo" );
				});
			});

			describe( "per instance storage", function(){
				it( title="structDelete of an accessor only affects that instance", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					var B = new accessorpool.pooled();
					structDelete( A, "getName" );
					B.setName( "bravo" );

					expect( function(){ A.getName(); } ).toThrow();
					expect( B.getName() ).toBe( "bravo" );
					expect( duplicate( B ).getName() ).toBe( "bravo" );
				});

				it( title="accessor keys are visible through the variables scope", body=function( currentSpec ){
					var A = new accessorpool.pooled();
					expect( structKeyExists( A, "getName" ) ).toBeTrue();
					expect( arrayFindNoCase( structKeyArray( A ), "addTag" ) ).toBeGT( 0 );
				});
			});

			describe( "concurrency", function(){
				// The pool is built in <clinit>, so class initialization is what publishes it; this pins
				// that a herd of threads racing the first instantiation all see one finished pool.
				it( title="32 concurrent instantiations all draw the same accessor objects", body=function( currentSpec ){
					var threadCount = 32;
					var threadNames = [];
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "accessorpool-concurrent-" & t;
						arrayAppend( threadNames, tname );
						thread name="#tname#" tid=t {
							var cfc = new accessorpool.pooled();
							cfc.setName( "t" & attributes.tid );
							thread.value = cfc.getName();
							thread.udfId = idOf( probeUdf( cfc, "getName" ) );
						}
					}
					thread action="join" name="#arrayToList( threadNames )#";

					var ids = {};
					for ( var t=1; t<=threadCount; t++ ) {
						var tname = "accessorpool-concurrent-" & t;
						var th = cfthread[ tname ];
						if ( th.status != "COMPLETED" ) throw( object=th.error );
						expect( th.value ).toBe( "t" & t );
						ids[ th.udfId ] = true;
						structDelete( cfthread, tname );
					}
					expect( structCount( ids ) ).toBe( 1 );
				});
			});

		});
	}

	// === Java-reflection helpers, same shape as test/tickets/LDEV6300.cfc ===

	private any function probeUdf( required any cfc, required string key ) {
		var clazz = createObject( "java", "java.lang.Class" ).forName( "lucee.runtime.ComponentImpl" );
		var udfsField = clazz.getDeclaredField( "_udfs" );
		udfsField.setAccessible( true );
		var keyImpl = createObject( "java", "lucee.runtime.type.KeyImpl" ).init( arguments.key );
		return udfsField.get( arguments.cfc ).get( keyImpl );
	}

	private any function srcOf( required any udf ) {
		var clazz = createObject( "java", "java.lang.Class" ).forName( "lucee.runtime.type.UDFGSProperty" );
		var srcField = clazz.getDeclaredField( "srcComponent" );
		srcField.setAccessible( true );
		return srcField.get( arguments.udf );
	}

	private numeric function idOf( required any obj ) {
		return createObject( "java", "java.lang.System" ).identityHashCode( arguments.obj );
	}

	private string function functionNames( required array functions ) {
		var names = [];
		for ( var fn in arguments.functions ) arrayAppend( names, lCase( fn.name ) );
		return arrayToList( names );
	}

}
