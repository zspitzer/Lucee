component extends="org.lucee.cfml.test.LuceeTestCase" {
	function run( testResults, testBox ) {
		describe( "Testcase for cfmodule tag", function() {
			it( title="cfmodule with template attribute", body=function( currentSpec ) {
				savecontent variable="local.out" {
					module template="module/greet.cfm" greeting="hello" who="world";
				}
				expect( trim( local.out ) ).toBe( "hello world" );
			});

			it( title="cfmodule with attributeCollection containing template key", body=function( currentSpec ) {
				var attrs = { template: "module/greet.cfm", greeting: "hello", who: "world" };
				savecontent variable="local.out" {
					module attributeCollection="#attrs#";
				}
				expect( trim( local.out ) ).toBe( "hello world" );
			});

			it( title="cfmodule passing custom attributes via attributeCollection", body=function( currentSpec ) {
				var attrs = { template: "module/greet.cfm", greeting: "hi", who: "lucee" };
				savecontent variable="local.out" {
					module attributeCollection="#attrs#";
				}
				expect( trim( local.out ) ).toBe( "hi lucee" );
			});

			it( title="cfmodule with single attribute via attributeCollection", body=function( currentSpec ) {
				var attrs = { template: "module/echo.cfm", value: "ping" };
				savecontent variable="local.out" {
					module attributeCollection="#attrs#";
				}
				expect( trim( local.out ) ).toBe( "ping" );
			});

		});
	}
}
