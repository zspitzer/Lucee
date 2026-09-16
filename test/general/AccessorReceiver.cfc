component extends="org.lucee.cfml.test.LuceeTestCase" labels="component" {

	// How a generated accessor finds the component it dispatches against.
	//
	// A UDFGSProperty is a bound method — _call ends in comp.getComponentScope() — and
	// UDFGSProperty.getComponent( pc ) resolves that comp in two tiers:
	//
	//   1. the component in ambient scope, pc.variablesScope() when it is a ComponentScope.
	//      This is the LDEV-1962 contract: an accessor mixed into a host CFC dispatches against
	//      the host, which is what ColdBox / WireBox virtual inheritance depends on. Pinned by
	//      test/tickets/LDEV1962.cfc.
	//   2. the declaring instance, when there is no component in ambient scope at all — a plain
	//      .cfm, or a struct held in one.
	//
	// LDEV-6298 v2's BoundUDF sits in front of both, but only on extraction through
	// ComponentImpl.get; a raw map copy out of a component is not wrapped.
	//
	// These specs pin tier 2 and the closure behaviour of tier 1. Pool identity and allocation
	// live in AccessorPool.cfc.

	function run( testResults, testBox ){
		describe( "generated accessor receiver resolution", function(){

			describe( "called by name from inside a closure", function(){
				it( title="resolves the receiver from the defining component", body=function( currentSpec ){
					var A = new accessorreceiver.closureCaller();
					var B = new accessorreceiver.closureCaller();
					A.setName( "alpha" );
					B.setName( "bravo" );
					expect( A.viaClosure() ).toBe( "alpha" );
					expect( B.viaClosure() ).toBe( "bravo" );
				});

				it( title="resolves through nested closures", body=function( currentSpec ){
					var A = new accessorreceiver.closureCaller();
					A.setName( "alpha" );
					expect( A.viaNestedClosure() ).toBe( "alpha" );
				});

				it( title="a setter called inside a closure writes the defining instance", body=function( currentSpec ){
					var A = new accessorreceiver.closureCaller();
					var B = new accessorreceiver.closureCaller();
					A.setViaClosure( "alpha" );
					expect( A.getName() ).toBe( "alpha" );
					expect( B.getName() ).toBe( "from-property" );
				});

				// LDEV-6298 v2 shares the accessor object between a component and its duplicate, so
				// resolving through the accessor's declaring instance would answer with the source here.
				it( title="a duplicate dispatches to the copy, not to the source", body=function( currentSpec ){
					var A = new accessorreceiver.closureCaller();
					A.setName( "alpha" );
					var D = duplicate( A );
					D.setName( "delta" );
					expect( D.viaClosure() ).toBe( "delta" );
					expect( A.viaClosure() ).toBe( "alpha" );
				});
			});

			describe( "copied out of the variables scope", function(){
				// Runs through _InternalRequest on purpose. A spec body is a closure declared inside this
				// test component, so calling the accessor here would find a component in ambient scope and
				// tier 1 would answer with the test component, masking what this pins: the declaring
				// instance answering when there is no component in scope at all. This is the shape Preside
				// boots through — RendererEncapsulator.cfm StructAppends a component's variables scope into
				// a plain template, and presideProxies.cfm then calls the accessor by name.
				it( title="dispatches against the component it was copied from when no component is in scope", body=function( currentSpec ){
					var result = _InternalRequest( template=createURI( "accessorreceiver/escaped.cfm" ) );
					expect( result.filecontent.trim() ).toBe( "alpha|bravo" );
				});
			});

		});
	}

	private string function createURI( required string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & arguments.calledName;
	}

}
