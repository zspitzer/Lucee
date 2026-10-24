component extends="org.lucee.cfml.test.LuceeTestCase" labels="java" {
	import lucee.runtime.type.KeyImpl;

	function run( testResults, testBox ) {
		describe( title = "KeyImpl: h64 folded from the original-case key", body = function() {

			// ß, µ, ÿ, ﬀ, dotless i, long s, CJK: toUpperCase changes length or leaves Latin-1
			var corpus = [
				"", "a", "Z", "lucee", "LUCEE", "LuCeE", "fooBar", "foo_bar", "$x", "x1y2z3",
				"a-b.c d", "@[`{", "aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789",
				"stra" & chr( 223 ) & "e", chr( 181 ), chr( 255 ), chr( 64256 ) & "x",
				chr( 305 ), chr( 383 ), chr( 20013 ) & chr( 25991 ),
				"ascii" & chr( 223 ) & "Tail", chr( 223 ) & "head"
			];

			it( title = "createHash64UC matches createHash64 over the upper-cased key", body = function() {
				loop array=corpus item="local.s" {
					var expected = toString( KeyImpl::createHash64( s.toUpperCase() ) );
					expect( toString( KeyImpl::createHash64UC( s ) ) ).toBe( expected, "key [#s#]" );
				}
			} );

			it( title = "a key's hash() is today's h64", body = function() {
				loop array=corpus item="local.s" {
					var expected = toString( KeyImpl::createHash64( s.toUpperCase() ) );
					expect( toString( KeyImpl::init( s ).hash() ) ).toBe( expected, "key [#s#]" );
				}
			} );

			it( title = "a key equals the key of its upper-cased spelling", body = function() {
				loop array=corpus item="local.s" {
					expect( KeyImpl::init( s ).equals( KeyImpl::init( s.toUpperCase() ) ) ).toBeTrue( "key [#s#]" );
					expect( KeyImpl::init( s ).getUpperString() ).toBeWithCase( s.toUpperCase(), "key [#s#]" );
				}
			} );

		} );
	}

}
