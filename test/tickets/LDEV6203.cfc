<cfscript>
component extends="org.lucee.cfml.test.LuceeTestCase" labels="orm,transaction" {

	public void function test() {
		local.uri = createURI( "LDEV6203/index.cfm" );
		local.result = _InternalRequest( uri );
		var content = result.filecontent.trim();
		// isolation should be TRANSACTION_NONE (0) before and after ORM usage
		expect( content ).toInclude( "before=0" );
		expect( content ).toInclude( "after=0" );
		expect( content ).toInclude( "true" );
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrenttemplatepath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}

}
</cfscript>
