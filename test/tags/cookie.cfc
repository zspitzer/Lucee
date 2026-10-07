component extends="org.lucee.cfml.test.LuceeTestCase" labels="cookie" {

	function beforeAll() {
		variables.vals = [
			{ name: "simple", value: "lucee", encoded: "lucee" },
			{ name: "MixedCase", value: "Lucee", encoded: "Lucee" },
			{ name: "guid", value: "376B3346-463E-4F79-83FFE7C41451304A", encoded: "376B3346-463E-4F79-83FFE7C41451304A" },
			{ name: "html", value: "space& & space & &nbsp;", encoded: "space%26%20%26%20space%20%26%20%26nbsp%3B" },
			// this will fail if not encoded as the delimiters should be encoded
			{ name: "delims", value: ";,=", encoded: "%3B%2C%3D" }
		];
		var res = _InternalRequest(
			template: createURI( "cookie/encode.cfm" ),
			form: { cookies: serializeJSON( variables.vals ) }
		);
		variables.setCookies = res.headers[ "Set-Cookie" ];
	}

	function run( testResults, testBox ) {
		describe( "cfcookie encodevalue", function() {

			it( "default encodes the value", function() {
				for ( var c in variables.vals ) {
					expect( getSetCookie( "DEFAULT_" & c.name ) ).toIncludeWithCase( "=" & c.encoded & ";" );
				}
			});

			it( "encodevalue=true encodes the value", function() {
				for ( var c in variables.vals ) {
					expect( getSetCookie( "ENCODED_" & c.name ) ).toIncludeWithCase( "=" & c.encoded & ";" );
				}
			});

			it( "encodevalue=false sends the value as is", function() {
				for ( var c in variables.vals ) {
					expect( getSetCookie( c.name ) ).toIncludeWithCase( "=" & c.value & ";" );
				}
			});
		});

		describe( "cfcookie name case", function() {

			it( "name is upper cased by default", function() {
				expect( getSetCookie( "simple" ) ).toIncludeWithCase( "SIMPLE=lucee;" );
				expect( getSetCookie( "MixedCase" ) ).toIncludeWithCase( "MIXEDCASE=Lucee;" );
				expect( getSetCookie( "ENCODED_simple" ) ).toIncludeWithCase( "ENCODED_SIMPLE=lucee;" );
			});

			it( "preservecase=true keeps the name case", function() {
				for ( var c in variables.vals ) {
					expect( getSetCookie( "preservecase_" & c.name ) ).toIncludeWithCase( "preservecase_" & c.name & "=" & c.value & ";" );
				}
			});

			it( "preservecase=true with a name from an unquoted struct key", function() {
				expect( getSetCookie( "preservecase_unquotedKey" ) ).toIncludeWithCase( "preservecase_UNQUOTEDKEY=lucee;" );
			});

			it( "preservecase=true with an unquoted name attribute", function() {
				expect( getSetCookie( "preservecase_UnquotedAttr" ) ).toIncludeWithCase( "preservecase_UnquotedAttr=lucee;" );
			});
		});
	}

	// raw Set-Cookie header for a cookie, the name is matched case insensitive
	private string function getSetCookie( required string cookieName ) {
		for ( var cc in variables.setCookies ) {
			if ( listFirst( cc, "=" ) == arguments.cookieName ) return cc;
		}
		throw "cookie [#arguments.cookieName#] not found in [#arrayToList( variables.setCookies, " | " )#]";
	}

	private string function createURI( string calledName ) {
		var baseURI = "/test/#listLast( getDirectoryFromPath( getCurrenttemplatepath() ), "\/" )#/";
		return baseURI & "" & calledName;
	}
}
