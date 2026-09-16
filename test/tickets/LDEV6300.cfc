component extends="org.lucee.cfml.test.LuceeTestCase" labels="java,component" {

	// LDEV-6300 — structural invariants underpinning the LDEV-6298 v2 flyweight share.
	//
	// CFML can't observe these contracts directly. A violation breaks the share gate at
	// duplicateUTFMap / addUDFS without breaking dispatch, so functional bedrock under
	// test/general/Accessors.cfc wouldn't catch it. The original investigation
	// (setOwner-corrupts-shared-UDFGSProperty) used Java-reflection tripwire scripts; this
	// file ports the durable invariant probes into TestBox so future refactors that touch
	// UDFGSProperty / ComponentImpl write paths land against red tests, not silent perf
	// regressions in tests-orm bench runs.

	function run( testResults, testBox ) {

		describe( "LDEV-6300 / LDEV-6298 v2 — flyweight share invariants", function(){

			// LDEV-3335 Option 2: the class level accessor pool is consumed by initProperties, so every
			// instance of a class draws the same accessor object and setProperty allocates nothing.
			it( title="fresh same-class siblings share one UDFGSProperty Java instance — LDEV-3335 accessor pool", body=function( currentSpec ){
				var A = new LDEV6300.Person();
				var B = new LDEV6300.Person();
				expect( idOf( probeUdf( A, "getName" ) ) ).toBe( idOf( probeUdf( B, "getName" ) ) );
			});

			// ComponentLoader.searchComponent still allocates a fresh ComponentImpl for the base on every
			// call, but both that base and a bare instantiation of it draw getName from the same pool, which
			// lives on the base class. The instances differ, the accessor does not.
			it( title="explicit-extends subclass and bare base share the base's pooled UDFGSProperty — LDEV-3335 accessor pool", body=function( currentSpec ){
				var base = new LDEV6300.BasePerson();
				var sub = new LDEV6300.InheritedPerson();
				expect( idOf( probeUdf( base, "getName" ) ) ).toBe( idOf( probeUdf( sub, "getName" ) ) );
				// child's own accessor still works — sanity check the fixture
				sub.setRole( "admin" );
				expect( sub.getRole() ).toBe( "admin" );
			});

			it( title="Duplicate(cfc) shares the UDFGSProperty Java instance with source — LDEV-6298 v2 contract", body=function( currentSpec ){
				var A = new LDEV6300.Person();
				A.setName( "alpha" );
				var D = duplicate( A );
				expect( idOf( probeUdf( A, "getName" ) ) ).toBe( idOf( probeUdf( D, "getName" ) ) );
				expect( D.getName() ).toBe( "alpha" );
			});

			// A pooled accessor is shared by every instance of the class, so its srcComponent is null for
			// its whole life — it must never be claimed by whichever instance happens to register it.
			it( title="pooled accessor has no srcComponent and sibling instantiation must not claim it", body=function( currentSpec ){
				var A = new LDEV6300.Person();
				expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
				var B = new LDEV6300.Person();
				expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
				expect( isNull( srcOf( probeUdf( B, "getName" ) ) ) ).toBeTrue();
			});

			it( title="pooled accessor srcComponent stays null across Duplicate(cfc) — duplicate path must not claim the share", body=function( currentSpec ){
				var A = new LDEV6300.Person();
				A.setName( "alpha" );
				expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
				var D = duplicate( A );
				expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
				expect( isNull( srcOf( probeUdf( D, "getName" ) ) ) ).toBeTrue();
			});

			// ObjectLoad rebuilds the instance and then walks _udfs / _data stamping the owner, which is
			// the third write site that has to leave a pooled accessor alone.
			it( title="pooled accessor srcComponent stays null across sibling ObjectSave/ObjectLoad — original LDEV-6298 investigation contract", body=function( currentSpec ){
				var A = new LDEV6300.Person();
				A.setName( "alpha" );
				var B = new LDEV6300.Person();
				B.setName( "bravo" );

				var C = new LDEV6300.Person();
				C.setName( "charlie" );
				var D = ObjectLoad( ObjectSave( C ) );

				expect( isNull( srcOf( probeUdf( A, "getName" ) ) ) ).toBeTrue();
				expect( isNull( srcOf( probeUdf( B, "getName" ) ) ) ).toBeTrue();
				expect( isNull( srcOf( probeUdf( D, "getName" ) ) ) ).toBeTrue();
				expect( D.getName() ).toBe( "charlie" );
				expect( A.getName() ).toBe( "alpha" );
				expect( B.getName() ).toBe( "bravo" );
			});

		});
	}

	// === Java-reflection helpers ===

	// _udfs is a private field on ComponentImpl; srcComponent is a private field declared on
	// UDFGSProperty. Both are needed to assert structural invariants that have no CFML surface.

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

}
