component extends="org.lucee.cfml.test.LuceeTestCase" labels="java,component" {

	// Pins the build-once cache contract on ComponentPageImpl.getFactory(pc): non-null on first
	// call, identical reference on subsequent calls, distinct per CFC class.

	function run( testResults, testBox ) {

		describe( "ComponentPageImpl.getFactory(pc) cache", function(){

			it( "returns a non-null ComponentFactory on first call", function(){
				var cp = cpOf( new pseudoBody.EmptyBody() );
				expect( isNull( getFactory( cp ) ) ).toBeFalse();
			});

			it( "returns the same instance on repeated calls (cache hit)", function(){
				var cp = cpOf( new pseudoBody.PropsOnly() );
				var f1 = getFactory( cp );
				var f2 = getFactory( cp );
				expect( idOf( f1 ) ).toBe( idOf( f2 ) );
			});

			it( "different CFC classes get distinct factory instances", function(){
				var cpA = cpOf( new pseudoBody.EmptyBody() );
				var cpB = cpOf( new pseudoBody.PropsOnly() );
				expect( idOf( getFactory( cpA ) ) ).notToBe( idOf( getFactory( cpB ) ) );
			});

		});
	}

	// === Java-reflection helpers ===

	private any function cpOf( required any cfc ) {
		var noClassArgs = javaCast( "java.lang.Class[]", [] );
		var noObjArgs   = javaCast( "java.lang.Object[]", [] );
		var compImplClass = createObject( "java", "java.lang.Class" ).forName( "lucee.runtime.ComponentImpl" );
		var getCpMethod   = compImplClass.getMethod( "_getComponentPageImpl", noClassArgs );
		return getCpMethod.invoke( arguments.cfc, noObjArgs );
	}

	private any function getFactory( required any cp ) {
		var pcClassArg = [ createObject( "java", "java.lang.Class" ).forName( "lucee.runtime.PageContext" ) ];
		var sig        = javaCast( "java.lang.Class[]", pcClassArg );
		var method     = arguments.cp.getClass().getMethod( "getFactory", sig );
		var args       = javaCast( "java.lang.Object[]", [ getPageContext() ] );
		return method.invoke( arguments.cp, args );
	}

	private numeric function idOf( required any obj ) {
		return createObject( "java", "java.lang.System" ).identityHashCode( arguments.obj );
	}

}
