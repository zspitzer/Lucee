component extends="org.lucee.cfml.test.LuceeTestCase" {

	function beforeAll(){
		systemOutput( "Waiting for server to be ready..., sleep 10s" );
		sleep(10000); // wait for server to be ready	
		variables.rounds = 10000;
	}

	function run( testResults, testBox ) {
		describe( title="Test suite for LDEV-5937 - optimise this.functionPaths", body=function() {

			it( title="single function path with one UDF", body=function( currentSpec ) {
				var uri = createURI( "LDEV5937/singlePathSingleFunc" );
				var result = "";
				loop times=#variables.rounds# {
					result = _InternalRequest( template: "#uri#/test.cfm" );
				}
				expect( result.filecontent.trim() ).toBe( "func1:ok" );
			});

			it( title="single function path with five UDFs", body=function( currentSpec ) {
				var uri = createURI( "LDEV5937/singlePathFiveFuncs" );
				var result = "";
				loop times=#variables.rounds# {
					result = _InternalRequest( template: "#uri#/test.cfm" );
				}
				expect( result.filecontent.trim() ).toBe( "func1:ok|func2:ok|func3:ok|func4:ok|func5:ok" );
			});

			// slow path, some UDF calls use different casing to the filenames
			it( title="three function paths with five UDFs each (lowercase files, mixed case calls)", body=function( currentSpec ) {
				var uri = createURI( "LDEV5937/threePathsFiveFuncsEach" );
				var result = "";
				loop times=#variables.rounds# {
					result = _InternalRequest( template: "#uri#/test.cfm" );
				}
				expect( result.filecontent.trim() ).toBe( "dir1func1:ok|dir1func5:ok|dir2func1:ok|dir2func5:ok|dir3func1:ok|dir3func5:ok" );
			});

			// fast path, test the assumption that UDF calls with use the same casing as the filenames
			it( title="three function paths with five UDFs each (camelCase files, matching calls)", body=function( currentSpec ) {
				var uri = createURI( "LDEV5937/threePathsCamelCase" );
				var result = "";
				loop times=#variables.rounds# {
					result = _InternalRequest( template: "#uri#/test.cfm" );
				}
				expect( result.filecontent.trim() ).toBe( "dir1func1:ok|dir1func5:ok|dir2func1:ok|dir2func5:ok|dir3func1:ok|dir3func5:ok" );
			});

			it( title="case insensitive function lookup", body=function( currentSpec ) {
				var uri = createURI( "LDEV5937/singlePathSingleFunc" );
				loop times=#variables.rounds# {
					var result = _InternalRequest( template: "#uri#/testCaseInsensitive.cfm" );
				}
				expect( result.filecontent.trim() ).toBe( "mixedcase:ok" );
			});

		});
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrenttemplatepath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}

}
