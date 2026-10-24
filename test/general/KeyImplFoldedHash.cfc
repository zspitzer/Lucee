component extends="org.lucee.cfml.test.LuceeTestCase" labels="java" {
	import lucee.runtime.type.KeyImpl;

	function run( testResults, testBox ) {
		describe( title = "KeyImpl: h64 folded from the original-case key", body = function() {

			// ß, µ, ÿ, ﬀ, dotless i, long s, CJK, dotted capital I: toUpperCase changes length or leaves Latin-1
			var corpus = [
				"", "a", "Z", "lucee", "LUCEE", "LuCeE", "fooBar", "foo_bar", "$x", "x1y2z3",
				"a-b.c d", "@[`{", "aBcDeFgHiJkLmNoPqRsTuVwXyZ0123456789",
				"stra" & chr( 223 ) & "e", chr( 181 ), chr( 255 ), chr( 64256 ) & "x",
				chr( 305 ), chr( 383 ), chr( 20013 ) & chr( 25991 ),
				"ascii" & chr( 223 ) & "Tail", chr( 223 ) & "head",
				chr( 304 ), chr( 304 ) & "stanbul"
			];

			// follows toUpperCase equality: lower-casing doesn't round trip for every char (dotted capital I)
			it( title = "createHash64UC follows toUpperCase", body = function() {
				loop array=corpus item="local.s" {
					var lc = s.toLowerCase();
					expect( toString( KeyImpl::createHash64UC( s.toUpperCase() ) ) ).toBe( toString( KeyImpl::createHash64UC( s ) ), "upper [#s#]" );
					expect( toString( KeyImpl::createHash64UC( lc.toUpperCase() ) ) ).toBe( toString( KeyImpl::createHash64UC( lc ) ), "lower [#s#]" );
				}
			} );

			it( title = "createHash64 and a key's hash() match createHash64UC", body = function() {
				loop array=corpus item="local.s" {
					var expected = toString( KeyImpl::createHash64UC( s ) );
					expect( toString( KeyImpl::createHash64( s ) ) ).toBe( expected, "createHash64 [#s#]" );
					expect( toString( KeyImpl::init( s ).hash() ) ).toBe( expected, "hash() [#s#]" );
				}
			} );

			it( title = "a key equals the key of its upper-cased spelling", body = function() {
				loop array=corpus item="local.s" {
					var k = KeyImpl::init( s );
					var uc = KeyImpl::init( s.toUpperCase() );
					expect( k.equals( uc ) ).toBeTrue( "key [#s#]" );
					expect( k.equalsIgnoreCase( uc ) ).toBeTrue( "equalsIgnoreCase [#s#]" );
					expect( k.hashCode() ).toBe( uc.hashCode(), "hashCode [#s#]" );
					expect( k.getUpperString() ).toBeWithCase( s.toUpperCase(), "key [#s#]" );
					var ucs = s.toUpperCase();
					for ( var i = 0; i < len( ucs ); i++ ) {
						expect( toString( k.upperCharAt( i ) ) ).toBeWithCase( mid( ucs, i + 1, 1 ), "upperCharAt #i# [#s#]" );
					}
				}
			} );

			it( title = "a key equals a String case-insensitively", body = function() {
				var k = KeyImpl::init( "fooBar" );
				expect( k.equals( "FOOBAR" ) ).toBeTrue();
				expect( k.equals( "foobar" ) ).toBeTrue();
				expect( k.equals( "fooBaz" ) ).toBeFalse();
				expect( k.equals( KeyImpl::init( "fooBaz" ) ) ).toBeFalse();
				expect( k.equalsIgnoreCase( KeyImpl::init( "fooBaz" ) ) ).toBeFalse();
			} );

			it( title = "no collisions over short names and case variants", body = function() {
				var chars = listToArray( "A,B,C,D,E,F,G,H,I,J,K,L,M,N,O,P,Q,R,S,T,U,V,W,X,Y,Z,0,1,2,3,4,5,6,7,8,9,_,$" );
				var names = [];
				loop array=chars item="local.a" {
					arrayAppend( names, a );
					loop array=chars item="local.b" {
						arrayAppend( names, a & b );
						arrayAppend( names, a & b & "x" );
					}
				}
				arrayAppend( names, corpus, true );
				var seen = {};
				var collisions = [];
				loop array=names item="local.s" {
					var uc = s.toUpperCase();
					var h = toString( KeyImpl::createHash64UC( s ) );
					if ( structKeyExists( seen, h ) && seen[ h ] != uc ) arrayAppend( collisions, "[#seen[ h ]#] [#uc#]" );
					seen[ h ] = uc;
				}
				expect( collisions ).toBeEmpty();
			} );

		} );
	}

}
