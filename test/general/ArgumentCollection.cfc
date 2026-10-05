component extends="org.lucee.cfml.test.LuceeTestCase" {

	/*
	 * argumentCollection semantics matrix.
	 *
	 * Pins the current Lucee semantics of the argumentCollection merge + defineArguments fill
	 * as an invariance guard for the zero-copy resolver rewrite. Cross-engine ground truth was
	 * captured 2026-08-12 against ACF 2025 (2025,0,12,331922) and Lucee 8.0.0.172-SNAPSHOT;
	 * every case comments where ACF agrees or diverges.
	 *
	 * Observables, in order of cross-engine stability:
	 *  - declared slot values ( arguments.name )  — agree on both engines
	 *  - positional reads ( arguments[ n ] )      — agree on both engines, including leftover order,
	 *    EXCEPT when a stranded int key shadows a slot ( case 05 ): Lucee resolves arguments[ n ]
	 *    key-first ( ArgumentImpl.get retries numeric keys as int only on a key MISS ), ACF reads
	 *    purely by position
	 *  - full key order ( structKeyList )         — Lucee-ONLY pin. Lucee appends leftovers after the
	 *    declared slots; ACF interleaves the forwarded scope's keys with the declared ones
	 *    (e.g. case 01: ACF keys=[A,P1,B,P2,C,D,E] vs Lucee a,b,c,d,e,p1,p2). Key order is
	 *    therefore NOT a compat observable — it is pinned here purely as Lucee invariance.
	 *
	 * Known cross-engine gaps (each also commented on its case):
	 *  - scalar collection: Lucee re-inserts the literal under key [argumentCollection], ACF drops it
	 *  - nested magic key inside a collection: Lucee keeps it as a literal leftover key, ACF
	 *    recursively expands it into the declared slots
	 *  - declared param NAMED argumentCollection with default=arguments (LDEV-4287 shape): both
	 *    engines agree the magic key does NOT expand when it arrives via a declared-param default
	 *  - full null support: unpassed optional args are absent on Lucee but present-as-null on ACF
	 *    for every call shape (LDEV-6188 — regression; skipped target expectations live in
	 *    test/tickets/LDEV6188.cfc)
	 *  - aliasing: ARRAYS inside the collection arrive by reference on Lucee but are copied on
	 *    ACF ( structs alias on both engines; container-level mutations reach neither )
	 */

	function afterAll() {
		application action="update" nullSupport=false;
	}

	function run( testResults, testBox ) {

		describe( title="argumentCollection routing into declared slots", body=function() {

			// ACF 2025 agrees: c=V3 d=V4, byPos=[<null>,<null>,V3,V4,<null>,V1,V2]
			it( title="01 pristine forward — int keys route to their own slots, caller names strand as leftovers", body=function() {
				var args = fwdPristine( "V1", "V2", "V3", "V4" );
				expect( isNull( args.a ) ).toBeTrue();
				expect( isNull( args.b ) ).toBeTrue();
				expect( args.c ).toBe( "V3" );
				expect( args.d ).toBe( "V4" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p1,p2" );
				expect( positionals( args ) ).toBe( "<null>,<null>,V3,V4,<null>,V1,V2" );
			});

			// ACF 2025 agrees: a positional key maps to the declared arg at its OWN integer, not at
			// its position within the iteration. Lucee <= 8.0.0.172 misrouted by iteration ordinal
			// here ( b=V3 c=V4 ); fixed to match ACF, ticket pending.
			it( title="02 forward after deleting the first named key — remaining int keys keep their own slots", body=function() {
				var args = fwdDeleteFirst( "V1", "V2", "V3", "V4" );
				expect( isNull( args.b ) ).toBeTrue();
				expect( args.c ).toBe( "V3" );
				expect( args.d ).toBe( "V4" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p2" );
				expect( positionals( args ) ).toBe( "<null>,<null>,V3,V4,<null>,V2" );
			});

			// ACF 2025 agrees: d=V4 e=V5. Same misroute family as case 02 ( pre-fix Lucee: c=V4 d=V5 ).
			it( title="03 forward after deleting a middle named key — int keys keep their own slots", body=function() {
				var args = fwdDeleteMiddle( "V1", "V2", "V3", "V4", "V5" );
				expect( isNull( args.c ) ).toBeTrue();
				expect( args.d ).toBe( "V4" );
				expect( args.e ).toBe( "V5" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p1,p3" );
				expect( positionals( args ) ).toBe( "<null>,<null>,<null>,V4,V5,V1,V3" );
			});

			// ACF 2025 agrees: numeric-string keys in a plain struct literal route positionally too
			// ( on Lucee this happens via the defineArguments int-key probe, not the merge )
			it( title="04 plain struct with numeric-string keys — routes positionally", body=function() {
				var args = target( argumentCollection = { "3": "V3", "4": "V4" } );
				expect( args.c ).toBe( "V3" );
				expect( args.d ).toBe( "V4" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

			// ACF 2025 agrees on the slots: c=NAMED d=V4, the colliding int key [3] strands as a
			// leftover key. Pre-fix Lucee: b=V3 c=NAMED d=V4, no strand.
			// DIVERGENCE on the positional view of the strand: Lucee resolves arguments[ 3 ]
			// key-first, so the stranded key [3] SHADOWS slot 3 ( V3 twice, NAMED only reachable
			// by name ); ACF reads byPos purely positionally ( [<null>,<null>,NAMED,V4,<null>,V2,V3] ).
			it( title="05 named arg collides with an int key's slot — named wins, int key strands as leftover", body=function() {
				var args = fwdCollision( "V1", "V2", "V3", "V4" );
				expect( args.c ).toBe( "NAMED" );
				expect( args.d ).toBe( "V4" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p2,3" );
				expect( positionals( args ) ).toBe( "<null>,<null>,V3,V4,<null>,V2,V3" );
			});

			// ACF 2025 agrees: a=NAMED — syntactic named args beat collection entries for the same key
			it( title="06 same key named at the call site AND in the collection — named wins", body=function() {
				var args = target( a = "NAMED", argumentCollection = { a: "COLL", b: "V2" } );
				expect( args.a ).toBe( "NAMED" );
				expect( args.b ).toBe( "V2" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

			// ACF 2025 agrees: b=V2 c=V3, p1 leftover
			it( title="07 forwarded scope holding an int-key tail — tail routes positionally", body=function() {
				var args = fwdTail( "V1", "V2", "V3" );
				expect( args.b ).toBe( "V2" );
				expect( args.c ).toBe( "V3" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p1" );
				expect( positionals( args ) ).toBe( "<null>,V2,V3,<null>,<null>,V1" );
			});

			// ACF 2025 agrees: [9] lands as leftover under its own key [9], readable positionally
			it( title="08 int key beyond the declared count — becomes a leftover under its own key", body=function() {
				var args = target( argumentCollection = { "9": "V9", b: "V2" } );
				expect( args.b ).toBe( "V2" );
				expect( args[ "9" ] ).toBe( "V9" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,9" );
				expect( positionals( args ) ).toBe( "<null>,V2,<null>,<null>,<null>,V9" );
			});

			// ACF 2025 agrees on the POSITIONAL order ( byPos=[...,VP,VX,VY,VZ] ) — that is the
			// compat observable. ACF's key LIST interleaves ( [A,P1,B,C,D,E,X,Y,Z] ); the key-order
			// pin below is Lucee invariance only.
			it( title="09 forwarded scope of non-declared names — leftover order is preserved", body=function() {
				var args = fwdLeftovers( "VP", "VX", "VY", "VZ" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,p1,x,y,z" );
				expect( positionals( args ) ).toBe( "<null>,<null>,<null>,<null>,<null>,VP,VX,VY,VZ" );
			});

			// DIVERGENCE: ACF 2025 silently DROPS a scalar collection ( keys=[A,B,C,D,E] );
			// Lucee re-inserts the literal as a leftover under key [argumentCollection].
			// Pinned as current Lucee behaviour — do not silently "fix" this in the resolver.
			it( title="10 scalar collection — Lucee re-inserts the literal as a leftover (ACF drops it)", body=function() {
				var args = target( argumentCollection = "SCALAR" );
				expect( args ).toHaveKey( "argumentCollection" );
				expect( args[ "argumentCollection" ] ).toBe( "SCALAR" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,argumentCollection" );
			});

			// ACF 2025 agrees: array elements map positionally
			it( title="11 array collection — elements map positionally", body=function() {
				var args = target( argumentCollection = [ "V1", "V2", "V3" ] );
				expect( args.a ).toBe( "V1" );
				expect( args.b ).toBe( "V2" );
				expect( args.c ).toBe( "V3" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

			// DIVERGENCE: ACF 2025 recursively EXPANDS a nested argumentCollection key
			// ( a=V1 b=V2, no literal key ); Lucee keeps it as a literal leftover and slot b
			// stays unfilled. Pinned as current Lucee behaviour.
			it( title="12 collection containing an argumentCollection key — Lucee keeps it literal (ACF expands)", body=function() {
				var args = target( argumentCollection = { a: "V1", argumentCollection: { b: "V2" } } );
				expect( args.a ).toBe( "V1" );
				expect( isNull( args.b ) ).toBeTrue();
				expect( args ).toHaveKey( "argumentCollection" );
				expect( args[ "argumentCollection" ].b ).toBe( "V2" );
			});

			// ACF 2025 agrees: degenerate control, all declared slots null
			it( title="13 empty struct collection — all declared slots stay null", body=function() {
				var args = target( argumentCollection = {} );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
				expect( positionals( args ) ).toBe( "<null>,<null>,<null>,<null>,<null>" );
			});

			// ACF 2025 agrees: a=V1 b=NB c=V3 — the mixed-shape happy path
			it( title="14 named arg plus collection filling different slots", body=function() {
				var args = target( b = "NB", argumentCollection = { a: "V1", c: "V3" } );
				expect( args.a ).toBe( "V1" );
				expect( args.b ).toBe( "NB" );
				expect( args.c ).toBe( "V3" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

			// LDEV-4287 shape. ACF 2025 behaves IDENTICALLY: the magic key does NOT expand when it
			// arrives via a declared-param default — arguments holds the literal key only. The
			// ticket's requested expansion would diverge from ACF.
			// Note the default expression evaluates in the CALLEE's context, so `arguments` binds
			// the closure's own in-construction scope ( a self-reference ) — the outer caller's
			// a/b are unreachable through it on both engines.
			it( title="15 declared param named argumentCollection, default=arguments — no expansion (matches ACF)", body=function() {
				var args = declaredParamDefault( "V1", "V2" );
				expect( args ).notToHaveKey( "a" );
				expect( args ).toHaveKey( "argumentCollection" );
				expect( structKeyList( args ) ).toBe( "argumentCollection" );
				// the self-reference: the nested value is the same scope, not the caller's
				expect( structKeyList( args[ "argumentCollection" ] ) ).toBe( "argumentCollection" );
			});

			// ACF 2025 agrees: c=V3STR, no leftover. A caller writing arguments[ 3 ] past its
			// scope size stores the plain STRING key "3" ( ArgumentImpl.setEL(int) falls back to
			// a string key — not an int key ), and the fill's int-key probe still matches it by
			// hash equality, filling slot 3.
			it( title="16 string-numeric key from a past-size positional write — fills its slot", body=function() {
				var args = fwdStrNumKey();
				expect( args.c ).toBe( "V3STR" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

			// DIVERGENCE: ACF 2025 gives the slot to the positional string key ( c=V3STR ) and
			// silently LOSES the named value — no leftover at all. Lucee lets named win and
			// strands the string key, same shadowing observable as case 05 ( the surviving key
			// "3" hides the positional read of slot 3 ). Pinned as current Lucee behaviour.
			it( title="17 string-numeric key loses its slot to a named key — drains as leftover", body=function() {
				var args = fwdStrNumCollision();
				expect( args.c ).toBe( "NAMED" );
				expect( args[ "3" ] ).toBe( "V3STR" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,3" );
				expect( positionals( args ) ).toBe( "<null>,<null>,V3STR,<null>,<null>,V3STR" );
			});

		});

		describe( title="other collection kinds", body=function() {

			// java.util.Map goes through its own merge branch ( keys copied verbatim, numeric-string
			// keys then route via the defineArguments int-key probe ). Lucee invariance pin — not
			// ACF-captured ( createObject java ).
			it( title="java.util.LinkedHashMap collection — named and numeric-string keys route like a struct", body=function() {
				var m = createObject( "java", "java.util.LinkedHashMap" ).init();
				m.put( "a", "V1" );
				m.put( "2", "V2" );
				m.put( "x", "VX" );
				var args = target( argumentCollection = m );
				expect( args.a ).toBe( "V1" );
				expect( args.b ).toBe( "V2" );
				expect( args.x ).toBe( "VX" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,x" );
			});

			// ordered struct literal — same Collection branch as a plain struct on Lucee
			// ( plain literals are already insertion-ordered here; on ACF they are not, which is
			// why the order-sensitive cases above forward an arguments scope instead )
			it( title="ordered struct literal collection", body=function() {
				var args = target( argumentCollection = [ b: "V2", a: "V1" ] );
				expect( args.a ).toBe( "V1" );
				expect( args.b ).toBe( "V2" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e" );
			});

		});

		describe( title="callee variety — same merge, different dispatch routes", body=function() {

			// CFC method via callWithNamedValues — same routing as a script UDF
			it( title="CFC method — int keys and names route identically to a UDF", body=function() {
				var c = new argcoll.Callee();
				var args = c.method( argumentCollection = { "2": "V2", x: "VX" } );
				expect( args.b ).toBe( "V2" );
				expect( args.x ).toBe( "VX" );
				expect( structKeyList( args ) ).toBe( "a,b,c,d,e,x" );
			});

			// generated property accessors run defineArguments with the UDFAddProperty funcArgs —
			// the int-key branch is load-bearing here ( 2-arg setter forms ). Lucee invariance pin.
			it( title="generated setter — argumentCollection routes by name and by int key", body=function() {
				var c = new argcoll.Callee();
				c.setPropA( argumentCollection = { propA: "BYNAME" } );
				expect( c.getPropA() ).toBe( "BYNAME" );
				c.setPropA( argumentCollection = { "1": "BYPOS" } );
				expect( c.getPropA() ).toBe( "BYPOS" );
			});

			// onMissingMethod receives the collection EXPANDED ( extension-axis merge with no
			// declared args: names and int keys land verbatim, nothing routes ). Lucee invariance pin.
			it( title="onMissingMethod — collection is expanded into missingMethodArguments", body=function() {
				var c = new argcoll.Callee();
				var args = c.nope( argumentCollection = { a: "V1", "2": "V2" } );
				expect( args ).toHaveKey( "a" );
				expect( args.a ).toBe( "V1" );
				expect( args[ "2" ] ).toBe( "V2" );
				expect( args ).notToHaveKey( "argumentCollection" );
			});

			// cachedWithin functions run defineArguments through _callCachedWithin — the collection
			// must participate in the cache key exactly like named args. Lucee invariance pin.
			it( title="cachedWithin function — collection values drive the cache key", body=function() {
				var c = new argcoll.Callee();
				var r1 = c.cached( argumentCollection = { a: "V1" } );
				expect( r1.a ).toBe( "V1" );
				var r2 = c.cached( argumentCollection = { a: "V1" } );
				expect( r2.calls ).toBe( r1.calls ); // same args — served from cache
				var r3 = c.cached( argumentCollection = { a: "V2" } );
				expect( r3.a ).toBe( "V2" );
				expect( r3.calls ).toBe( r1.calls + 1 ); // different args — fresh call
			});

		});

		describe( title="defaults, required and re-entrancy", body=function() {

			// getDefaultValue can evaluate arbitrary CFML — here the default expression itself
			// calls a UDF via argumentCollection, re-entering defineArguments mid-fill. This is
			// the re-entrancy hazard the zero-copy resolver's per-call staging must survive.
			it( title="expression default re-enters defineArguments mid-fill", body=function() {
				var args = defExpr( argumentCollection = {} );
				expect( args.a ).toBe( "def-DX" );
				expect( args.b ).toBe( "LIT" );
			});

			// exact exception wording is an observable — frameworks match on it
			it( title="required arg missing from the collection — exception text", body=function() {
				try {
					reqFn( argumentCollection = { b: "V2" } );
					fail( "should have thrown" );
				}
				catch ( any ex ) {
					expect( ex.message ).toInclude( "[a]" );
					expect( ex.message ).toInclude( "is required but was not passed in" );
				}
			});

			it( title="required arg satisfied by an int key in the collection", body=function() {
				var args = reqFn( argumentCollection = { "1": "V1" } );
				expect( args.a ).toBe( "V1" );
			});

		});

		describe( title="mutation and aliasing — what the callee shares with the source", body=function() {

			// ACF 2025 agrees: structs arrive BY REFERENCE — routed into a declared slot or not,
			// the callee mutates the caller's struct
			it( title="struct in a declared slot — callee mutation reaches the source (both engines)", body=function() {
				var src = { k: "ORIG" };
				mutStruct( argumentCollection = { s1: src } );
				expect( src.k ).toBe( "MUT" );
			});

			it( title="struct arriving as a leftover — callee mutation reaches the source (both engines)", body=function() {
				var src = { k: "ORIG" };
				mutLeftoverStruct( argumentCollection = { a: "V1", lo: src } );
				expect( src.k ).toBe( "MUT" );
			});

			// DIVERGENCE: arrays arrive BY REFERENCE on Lucee ( default passby ) — the callee's
			// append reaches the source. ACF 2025 copies arrays into arguments ( src stays len 1 ).
			it( title="array in a declared slot — shared by reference on Lucee (ACF copies)", body=function() {
				var src = [ "ORIG" ];
				mutArray( argumentCollection = { arr: src } );
				expect( arrayLen( src ) ).toBe( 2 );
			});

			it( title="array arriving as a leftover — shared by reference on Lucee (ACF copies)", body=function() {
				var src = [ "ORIG" ];
				mutLeftoverArray( argumentCollection = { a: "V1", loArr: src } );
				expect( arrayLen( src ) ).toBe( 2 );
			});

			// Lucee invariance pin: passby="value" ( cfargument tag syntax ) must still clone when
			// the arg arrives via the collection — castToAndClone firing on the argcoll path
			// ( no ACF equivalent; ACF has no passby attribute )
			it( title="passby=value arg (tag syntax) via collection — callee gets a clone", body=function() {
				var src = [ "ORIG" ];
				new argcoll.CalleeTag().mutArrayByValue( argumentCollection = { arr: src } );
				expect( arrayLen( src ) ).toBe( 1 );
			});

			// COMPILER GAP ( unticketed 2026-08-12 ): script-syntax passby="value" is silently
			// ignored — AbstrCFMLScriptTransformer hardcodes passByRef=true and stores inline arg
			// attributes as metadata only. Pinned as CURRENT behaviour ( by reference ); this row
			// flips when the parser gap is fixed.
			it( title="passby=value arg (script syntax) — currently ignored, arrives by reference", body=function() {
				var src = [ "ORIG" ];
				mutArrayByValue( argumentCollection = { arr: src } );
				expect( arrayLen( src ) ).toBe( 2 );
			});

			// ACF 2025 agrees on container independence: rebinding a slot, inserting a fresh key
			// or deleting one inside the callee never reaches the source collection/scope —
			// the merge copies entries into a fresh container
			it( title="rebind + insert inside the callee — source collection unaffected (both engines)", body=function() {
				var coll = { a: "ORIG", b: "V2" };
				mutRebind( argumentCollection = coll );
				expect( coll.a ).toBe( "ORIG" );
				expect( coll ).notToHaveKey( "fresh" );
			});

			it( title="delete inside the callee — forwarded caller scope unaffected (both engines)", body=function() {
				var r = fwdDeleteInside( "V1", "LO" );
				expect( r ).toBeTrue();
			});

		});

		describe( title="argumentCollection under full null support (runtime toggle)", body=function() {

			beforeEach( function( currentSpec, data ) {
				application action="update" nullSupport=true;
			});

			afterEach( function( currentSpec, data ) {
				application action="update" nullSupport=false;
			});

			// LDEV-6188: ACF 2025 with nullSupport on has ALL declared keys present with unpassed
			// args as null, for every call shape. Lucee currently leaves them ABSENT. The rows
			// below pin CURRENT Lucee behaviour as invariance for the resolver rewrite — they are
			// expected to flip when the LDEV-6188 fix lands ( target expectations are the skipped
			// rows in test/tickets/LDEV6188.cfc ).

			it( title="argumentCollection={} — unpassed optional args currently absent (LDEV-6188: ACF has them present-as-null)", body=function() {
				var args = target( argumentCollection = {} );
				expect( args ).notToHaveKey( "a" );
				expect( args ).notToHaveKey( "b" );
			});

			it( title="partial collection — passed key present, unpassed currently absent (LDEV-6188: ACF present-as-null)", body=function() {
				var args = target( argumentCollection = { a: "V1" } );
				expect( args.a ).toBe( "V1" );
				expect( args ).notToHaveKey( "b" );
			});

			// both engines agree the default fills; the gap is only the unpassed no-default key
			// ( ACF: present-as-null; Lucee: absent )
			it( title="declared default fills under full null support (both engines agree on the default)", body=function() {
				var args = withDefault( argumentCollection = {} );
				expect( args.a ).toBe( "D1" );
				expect( args ).notToHaveKey( "b" );
			});

		});

	}

	// ---- helpers: the standard callee plus the forwarding caller shapes ----

	// the standard callee: 5 untyped optional args, returns its own arguments scope
	private function target( a, b, c, d, e ) {
		return arguments;
	}

	private function withDefault( a = "D1", b ) {
		return arguments;
	}

	private function fwdPristine( p1, p2 ) {
		return target( argumentCollection = arguments );
	}

	private function fwdDeleteFirst( p1, p2 ) {
		structDelete( arguments, "p1" );
		return target( argumentCollection = arguments );
	}

	private function fwdDeleteMiddle( p1, p2, p3 ) {
		structDelete( arguments, "p2" );
		return target( argumentCollection = arguments );
	}

	private function fwdCollision( p1, p2 ) {
		structDelete( arguments, "p1" );
		return target( c = "NAMED", argumentCollection = arguments );
	}

	private function fwdTail( p1 ) {
		return target( argumentCollection = arguments );
	}

	private function fwdLeftovers( p1, x, y, z ) {
		return target( argumentCollection = arguments );
	}

	// past-size positional write: stores the plain string key "3", not an int key
	private function fwdStrNumKey() {
		arguments[ 3 ] = "V3STR";
		return target( argumentCollection = arguments );
	}

	private function fwdStrNumCollision() {
		arguments[ 3 ] = "V3STR";
		arguments.c = "NAMED";
		return target( argumentCollection = arguments );
	}

	private function mutStruct( s1, s2 ) {
		arguments.s1.k = "MUT";
	}

	private function mutLeftoverStruct( a ) {
		arguments.lo.k = "MUT";
	}

	private function mutArray( arr ) {
		arrayAppend( arguments.arr, "MUT" );
	}

	private function mutArrayByValue( any arr passby="value" ) {
		arrayAppend( arguments.arr, "MUT" );
	}

	private function mutLeftoverArray( a ) {
		arrayAppend( arguments.loArr, "MUT" );
	}

	private function mutRebind( a, b ) {
		arguments.a = "REBOUND";
		arguments.fresh = "NEW";
	}

	private function deleteInside( a ) {
		structDelete( arguments, "lo" );
	}

	private function fwdDeleteInside( a, lo ) {
		deleteInside( argumentCollection = arguments );
		return structKeyExists( arguments, "lo" );
	}

	private function defExpr( a = makeDefault(), b = "LIT" ) {
		return arguments;
	}

	private function makeDefault() {
		return inner( argumentCollection = { x: "DX" } );
	}

	private function inner( x ) {
		return "def-" & arguments.x;
	}

	private function reqFn( required a, b ) {
		return arguments;
	}

	// LDEV-4287 shape: closure DECLARES a parameter named argumentCollection, default = arguments
	private function declaredParamDefault( a, b ) {
		var inner = function( argumentCollection = arguments ) {
			return arguments;
		};
		return inner();
	}

	// positional reads across the whole scope, declared slots first then leftovers —
	// this is the cross-engine-stable view of leftover ORDER
	private function positionals( args ) {
		var out = [];
		var total = structCount( args );
		for ( var p = 1; p <= total; p++ ) {
			if ( isNull( args[ p ] ) ) arrayAppend( out, "<null>" );
			else arrayAppend( out, args[ p ] );
		}
		return arrayToList( out );
	}

}
