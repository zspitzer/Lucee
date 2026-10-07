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

			it( title="cfmodule with template and custom attributes in one struct, same struct twice", body=function( currentSpec ) {
				var attrs = { template: "module/greet.cfm", greeting: "hey", who: "again" };
				for ( var i = 1; i <= 2; i++ ) {
					savecontent variable="local.out" {
						module attributeCollection="#attrs#";
					}
					expect( trim( local.out ) ).toBe( "hey again" );
					expect( listSort( structKeyList( attrs ), "textnocase" ) ).toBe( "greeting,template,who" );
					expect( structCount( attrs ) ).toBe( 3 );
				}
			});

		});

		describe( "cfmodule custom tag behaviour", function() {
			it( title="thisTag.generatedContent can be rewritten in the end pass", body=function( currentSpec ) {
				savecontent variable="local.out" {
					module template="module/upper.cfm" {
						echo( "shout" );
					}
				}
				expect( trim( local.out ) ).toBeWithCase( "SHOUT" );
			});

			it( title="thisTag.hasEndTag is false without a body, start pass only", body=function( currentSpec ) {
				savecontent variable="local.out" {
					module template="module/endTag.cfm";
				}
				expect( trim( local.out ) ).toBe( "start:false;" );
			});

			it( title="thisTag.hasEndTag is true with a body, start and end pass", body=function( currentSpec ) {
				savecontent variable="local.out" {
					module template="module/endTag.cfm" {}
				}
				expect( trim( local.out ) ).toBe( "start:true;end:true;" );
			});

			it( title="caller variables are only visible via the caller scope", body=function( currentSpec ) {
				variables.moduleCallerOnly = 1;
				savecontent variable="local.out" {
					module template="module/isolation.cfm";
				}
				expect( trim( local.out ) ).toBe( "false:true" );
			});

			it( title="missing template throws, naming the template", body=function( currentSpec ) {
				expect( function() {
					module template="module/doesNotExist.cfm";
				}).toThrow( regex="doesNotExist\.cfm" );
			});
		});
	}
}
