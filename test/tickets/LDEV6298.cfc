component extends="org.lucee.cfml.test.LuceeTestCase" labels="component" {

	// LDEV-6298 v2 — existence probes on a component (structKeyExists(obj,"setX") from DI
	// frameworks is the hot caller) must answer the same for accessors, plain UDFs, data
	// members, null-valued members and missing keys, with and without full null support.
	// ComponentImpl.contains() resolves the member directly instead of going through get(),
	// which would allocate a discarded BoundUDF per probe.

	function run( testResults, testBox ) {

		describe( "LDEV-6298 v2 — component existence probes", function(){

			it( title="structKeyExists on accessors, UDFs, data members and missing keys, nullSupport=false", body=function( currentSpec ){
				var r = probe( false );
				expect( r.getter ).toBeTrue();
				expect( r.setter ).toBeTrue();
				expect( r.udf ).toBeTrue();
				expect( r.property ).toBeFalse();
				expect( r.plain ).toBeTrue();
				expect( r.nada ).toBeFalse();
				expect( r.missing ).toBeFalse();
				expect( r.isDefinedSetter ).toBeTrue();
				expect( r.isDefinedMissing ).toBeFalse();
				expect( r.extracted ).toBeTrue();
				expect( r.called ).toBe( "alpha" );
			});

			it( title="structKeyExists on accessors, UDFs, data members and missing keys, nullSupport=true", body=function( currentSpec ){
				var r = probe( true );
				expect( r.getter ).toBeTrue();
				expect( r.setter ).toBeTrue();
				expect( r.udf ).toBeTrue();
				expect( r.property ).toBeFalse();
				expect( r.plain ).toBeTrue();
				expect( r.nada ).toBeTrue();
				expect( r.missing ).toBeFalse();
				expect( r.isDefinedSetter ).toBeTrue();
				expect( r.isDefinedMissing ).toBeFalse();
				expect( r.extracted ).toBeTrue();
				expect( r.called ).toBe( "alpha" );
			});

		});
	}

	private struct function probe( required boolean nullSupport ) {
		var result = _InternalRequest(
			template: createURI( "LDEV6298/probe.cfm" ),
			url: { nullSupport: arguments.nullSupport }
		);
		return deserializeJSON( result.filecontent.trim() );
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}

}
