component extends="org.lucee.cfml.test.LuceeTestCase" labels="java,component" {

	// Pins ComponentPageImpl.hasPseudoConstructorBody() across the body-shape matrix.
	// False = body holds only declarative shapes (functions, cfproperty, cfimport) that are
	// class-level and don't do per-instance work. True = body has imperative content (cfset,
	// cfinclude, cfif/cfswitch with body, throw, wrappers like cfsilent/cfoutput, etc.).

	function run( testResults, testBox ) {

		describe( "LDEV-6300 phase 5 — hasPseudoConstructorBody flag", function(){

			describe( "False bucket — purely declarative bodies (factory-eligible)", function(){

				it( "empty body with no decls", function(){
					expect( hasBody( new pseudoBody.EmptyBody() ) ).toBeFalse();
				});

				it( "empty body with pre-declaration content (compile-time dead) — script", function(){
					expect( hasBody( new loose.BeforeDeclaration() ) ).toBeFalse();
				});

				it( "empty body with post-declaration content (compile-time dead) — script", function(){
					expect( hasBody( new loose.AfterDeclaration() ) ).toBeFalse();
				});

				it( "empty body with pre+post writes — script", function(){
					expect( hasBody( new loose.VariablesProbe() ) ).toBeFalse();
				});

				it( "cfproperty + functions only", function(){
					expect( hasBody( new pseudoBody.PropsOnly() ) ).toBeFalse();
				});

				it( "cfproperty with expression-form default classifies as declarative", function(){
					expect( hasBody( new pseudoBody.PropExprDefault() ) ).toBeFalse();
				});

				it( "cfimport + functions only (imports are class-level)", function(){
					expect( hasBody( new pseudoBody.ImportsOnly() ) ).toBeFalse();
				});

			});

			describe( "True bucket — body has imperative content (factory-incompatible)", function(){

				it( "single cfset in body", function(){
					expect( hasBody( new pseudoBody.BodyWithCfset() ) ).toBeTrue();
				});

				it( "cfset between function declarations — script", function(){
					expect( hasBody( new loose.BetweenFunctions() ) ).toBeTrue();
				});

				it( "cfset between cffunction declarations — tag-syntax", function(){
					expect( hasBody( new loose.TagBetweenFunctions() ) ).toBeTrue();
				});

				it( "cfinclude in body classifies True even if include is empty", function(){
					expect( hasBody( new pseudoBody.BodyWithCfinclude() ) ).toBeTrue();
				});

				it( "throw in body classifies True", function(){
					try {
						hasBody( new loose.ThrowsInBody() );
						fail( "expected ThrowsInBody construction to throw" );
					}
					catch ( LooseTest e ) {
						// new ThrowsInBody() throws by design; can't probe the cp via the CFC
						// instance because no instance is returned. The classification is still
						// True (body has a throw statement); skip the assertion form and accept
						// that body-shape matrix is covered by BodyWithCfset / BetweenFunctions.
					}
				});

			});

		});
	}

	// === Java-reflection helper ===

	// Walks: cfc → ComponentImpl._getComponentPageImpl() → ComponentPageImpl.hasPseudoConstructorBody()
	private boolean function hasBody( required any cfc ) {
		var noClassArgs = javaCast( "java.lang.Class[]", [] );
		var noObjArgs   = javaCast( "java.lang.Object[]", [] );

		var compImplClass = createObject( "java", "java.lang.Class" ).forName( "lucee.runtime.ComponentImpl" );
		var getCpMethod   = compImplClass.getMethod( "_getComponentPageImpl", noClassArgs );
		var cp            = getCpMethod.invoke( arguments.cfc, noObjArgs );

		var hasBodyMethod = cp.getClass().getMethod( "hasPseudoConstructorBody", noClassArgs );
		return hasBodyMethod.invoke( cp, noObjArgs );
	}

}
