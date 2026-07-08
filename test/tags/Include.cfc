component extends="org.lucee.cfml.test.LuceeTestCase" {
	function run( testResults, testBox ) {
		describe( "Testcase for cfinclude tag", function() {

			it( title="basic cfinclude with no template attribute", body=function( currentSpec ) {
				savecontent variable="local.out" {
					include "include/helper.cfm";
				}
				expect( trim( local.out ) ).toBe( "included" );
			});


			it( title="basic cfinclude with template attribute", body=function( currentSpec ) {
				savecontent variable="local.out" {
					include template="include/helper.cfm";
				}
				expect( trim( local.out ) ).toBe( "included" );
			});



		});
	}
}
