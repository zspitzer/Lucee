component extends="org.lucee.cfml.test.LuceeTestCase" labels="localmode" {

	function run( testResults, testBox ) {
		describe( "LDEV-2919: Component-level localmode", function() {

			it( "component localmode=modern should apply to all functions", function() {
				var cfc = new LDEV2919.ModernComponent();
				// In modern mode, 'x' should be local-scoped and not leak to variables
				var result = cfc.testLocalScope();
				expect( result.localValue ).toBe( 42 );
				expect( result.variablesHasX ).toBe( false );
			});

			it( "component localmode=classic should allow variables scope leakage", function() {
				var cfc = new LDEV2919.ClassicComponent();
				// In classic mode, 'x' without var should leak to variables scope
				var result = cfc.testLocalScope();
				expect( result.localValue ).toBe( 42 );
				expect( result.variablesHasX ).toBe( true );
			});

			it( "function localmode should override component localmode", function() {
				var cfc = new LDEV2919.ModernComponentWithClassicFunction();
				// Component is modern, but function explicitly sets classic
				var result = cfc.testClassicOverride();
				expect( result.variablesHasY ).toBe( true );
			});

			it( "extended component should inherit parent localmode", function() {
				var cfc = new LDEV2919.ExtendedModernComponent();
				// Parent is modern, child doesn't specify - should inherit modern
				var result = cfc.testInheritedLocalScope();
				expect( result.variablesHasZ ).toBe( false );
			});

			it( "child component can override parent localmode", function() {
				var cfc = new LDEV2919.ClassicExtendingModern();
				// Parent is modern, child explicitly sets classic
				var result = cfc.testOverriddenLocalScope();
				expect( result.variablesHasW ).toBe( true );
			});

			it( "component without localmode should inherit from Application.cfc modern", function() {
				// Uses internalRequest to hit Application.cfc with this.localmode="modern"
				var result = _InternalRequest(
					template: "#createURI( 'LDEV2919' )#/appModern/test.cfm"
				);
				expect( result.filecontent.trim() ).toBe( "false" );
			});

			it( "component without localmode should inherit from Application.cfc classic", function() {
				// Uses internalRequest to hit Application.cfc with this.localmode="classic"
				var result = _InternalRequest(
					template: "#createURI( 'LDEV2919' )#/appClassic/test.cfm"
				);
				expect( result.filecontent.trim() ).toBe( "true" );
			});

			it( "component localmode should override Application.cfc localmode", function() {
				// Application.cfc is classic, but component is modern - component wins
				var result = _InternalRequest(
					template: "#createURI( 'LDEV2919' )#/appClassic/testModernOverride.cfm"
				);
				expect( result.filecontent.trim() ).toBe( "false" );
			});

			it( "component metadata should include resolved localmode", function() {
				var cfc = new LDEV2919.ModernComponent();
				var meta = getMetaData( cfc );
				expect( meta.localMode ).toBe( "modern" );
			});

			it( "inherited component metadata should show resolved localmode", function() {
				var cfc = new LDEV2919.ExtendedModernComponent();
				var meta = getMetaData( cfc );
				// Child inherits from parent, metadata should show resolved value
				expect( meta.localMode ).toBe( "modern" );
			});

			it( "component without localmode should not have localmode in metadata", function() {
				var cfc = new LDEV2919.NoLocalModeComponent();
				var meta = getMetaData( cfc );
				// No localmode specified and no parent - should not appear in metadata
				expect( structKeyExists( meta, "localMode" ) ).toBe( false );
			});

			it( "localmode=true should behave as modern", function() {
				var cfc = new LDEV2919.BooleanTrueComponent();
				var result = cfc.testLocalScope();
				expect( result.variablesHasX ).toBe( false );
			});

			it( "localmode=false should behave as classic", function() {
				var cfc = new LDEV2919.BooleanFalseComponent();
				var result = cfc.testLocalScope();
				expect( result.variablesHasX ).toBe( true );
			});

			it( "static functions should respect component localmode", function() {
				// Static function in modern component should use local scope
				var result = LDEV2919.ModernComponent::testStaticLocalScope();
				expect( result.variablesHasX ).toBe( false );
			});

			it( "multi-level inheritance should resolve localmode correctly", function() {
				// Grandparent=modern, Parent=inherit, Child=inherit
				var cfc = new LDEV2919.GrandchildComponent();
				var result = cfc.testLocalScope();
				expect( result.variablesHasX ).toBe( false );
			});

		});
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrentTemplatePath() ), "\/" )#/";
		return baseURI & calledName;
	}

}
