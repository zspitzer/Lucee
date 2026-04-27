component extends="org.lucee.cfml.test.LuceeTestCase" labels="session" {

	function run( testResults, testBox ) {
		describe( "LDEV-6289: JEE session silently replaced when HttpSession state diverges", function() {

			it( title="session data preserved when underlying HttpSession invalidated (CFID cookie still valid)",
				skip=isJsr223(),
				body=function( currentSpec ) {
					var ctx = newContext();
					var d1 = json( req( ctx, "/set.cfm", { user: "zac" } ) );
					expect( d1.sessionUser ).toBe( "zac" );

					// Tomcat invalidates the HttpSession (shutdown / app reload /
					// passivation / container session-fixation). User's CFID cookie
					// is still valid — session data should survive.
					req( ctx, "/invalidate-tomcat.cfm" );

					var d3 = json( req( ctx, "/get.cfm" ) );
					expect( d1.cfid ).toBe( d3.cfid, "CFID cookie should be unchanged" );
					expect( d3.sessionUser ).toBe( "zac",
						"session.user should survive HttpSession invalidate — see LDEV-6289 Finding ##1" );
				});

			it( title="session data preserved when JSESSIONID cookie lost but CFID kept (browser-restart)",
				skip=isJsr223(),
				body=function( currentSpec ) {
					var ctx = newContext();
					var d1 = json( req( ctx, "/set.cfm", { user: "zac" } ) );
					expect( d1.sessionUser ).toBe( "zac" );

					// JSESSIONID is a session cookie by default; CFID is long-lived.
					// Browser close drops the former while keeping the latter.
					structDelete( ctx.cookies, "JSESSIONID" );

					var d2 = json( req( ctx, "/get.cfm" ) );
					expect( d1.cfid ).toBe( d2.cfid, "CFID cookie should be unchanged" );
					expect( d2.sessionUser ).toBe( "zac",
						"session.user should survive JSESSIONID loss — see LDEV-6289" );
				});

			it( title="onSessionEnd does NOT fire on container-driven invalidate when CFID is still valid",
				skip=isJsr223(),
				body=function( currentSpec ) {
					// Companion assertion to test #1: the rebind contract means no
					// JSession is displaced when HttpSession is invalidated by
					// non-Lucee code while the CFID cookie is still alive — so
					// onSessionEnd should NOT fire (firing would contradict the
					// data-preservation contract that test #1 asserts).
					var ctx = newContext();
					req( ctx, "/clear-events.cfm" );
					req( ctx, "/set.cfm", { user: "zac" } );
					req( ctx, "/invalidate-tomcat.cfm" );
					req( ctx, "/get.cfm" );

					var events = json( req( ctx, "/events.cfm" ) );
					var endCount = 0;
					for ( var e in events ) {
						if ( e.kind eq "onSessionEnd" ) endCount++;
					}
					expect( endCount ).toBe( 0,
						"onSessionEnd must not fire when JSession is rebound — data is preserved across container-driven invalidate (see LDEV-6289 design)" );
				});

			it( title="session data preserved when Lucee sessionTimeout fires while HttpSession is still alive (LDEV-2973 trigger path)",
				skip=isJsr223(),
				body=function( currentSpec ) {
					// jee-short app has sessionTimeout=2s. After SET, Lucee bumps
					// Tomcat's maxInactiveInterval to 62s, so HttpSession survives.
					// Wait 4s without touching session — JSession.isExpired() will
					// return true on next access. ScopeContext takes the IF/expired
					// branch added by LDEV-2973 (c0e23bdae) and calls createNewJSession,
					// which today silently overwrites cfSessionContexts.
					var ctx = newContext( "jee-short" );
					var d1 = json( req( ctx, "/set.cfm", { user: "zac" } ) );
					expect( d1.sessionUser ).toBe( "zac" );

					sleep( 4000 );

					var d3 = json( req( ctx, "/get.cfm" ) );
					expect( d1.cfid ).toBe( d3.cfid, "CFID cookie should be unchanged" );
					expect( d3.sessionUser ).toBe( "zac",
						"session.user should survive Lucee-clock expiry when CFID is still valid — see LDEV-6289" );
				});
		});
	}

	// -- helpers ---------------------------------------------------------

	private boolean function isJsr223() {
		// Skip when running via script-runner — no real HttpSession.
		return ( cgi.request_url eq "http://localhost/index.cfm" );
	}

	private struct function newContext( string app="jee" ) {
		return { cookies: {}, app: arguments.app };
	}

	// req: cfhttp wrapper that maintains a cookie jar in the ctx struct.
	// ctx.app picks the sub-app: "jee" (30 min timeout) or "jee-short" (2s timeout).
	// Returns the raw cfhttp result (use json() to deserialize fileContent).
	private struct function req( required struct ctx, required string path, struct args={} ) {
		var hostIdx = find( cgi.script_name, cgi.request_url );
		if ( hostIdx eq 0 ) throw "failed to extract host from cgi.request_url [#cgi.request_url#]";
		var host = left( cgi.request_url, hostIdx - 1 );
		var webUrl = host & "/test/tickets/LDEV6289/" & arguments.ctx.app & arguments.path;

		var result = "";
		http method="get" url="#webUrl#" result="result" {
			for ( var name in arguments.ctx.cookies ) {
				httpparam type="cookie" name="#name#" value="#arguments.ctx.cookies[ name ]#";
			}
			for ( var k in arguments.args ) {
				httpparam type="url" name="#k#" value="#arguments.args[ k ]#";
			}
		}

		// Capture Set-Cookie headers into the ctx jar.
		if ( structKeyExists( result.responseHeader, "Set-Cookie" ) ) {
			var raw = result.responseHeader[ "Set-Cookie" ];
			var lines = isArray( raw ) ? raw : [ raw ];
			for ( var line in lines ) {
				var pair = listFirst( line, ";" );
				var n = listFirst( pair, "=" );
				var v = listLen( pair, "=" ) gte 2 ? listLast( pair, "=" ) : "";
				arguments.ctx.cookies[ n ] = v;
			}
		}
		return result;
	}

	private any function json( required struct httpResult ) {
		return deserializeJson( arguments.httpResult.fileContent );
	}
}
