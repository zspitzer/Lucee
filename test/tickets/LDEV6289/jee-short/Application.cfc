component {
	this.name = "ldev6289-jee-short";
	this.sessionManagement = true;
	this.setClientCookies = true;
	this.sessionType = "jee";
	this.sessionStorage = "memory";
	// 2 second timeout — to drive the LDEV-2973 trigger path: existing
	// JSession in HttpSession attribute, jSession.isExpired() returns true.
	// Lucee bumps Tomcat's maxInactiveInterval to (sessionTimeout + 60s) = 62s
	// so the underlying HttpSession lives longer than the Lucee JSession.
	// Wait > 2s but < 62s without touching session, then make a session-touching
	// request — IF/expired branch in getJSessionScope calls createNewJSession.
	this.sessionTimeout = createTimespan( 0, 0, 0, 2 );
	this.applicationTimeout = createTimespan( 0, 1, 0, 0 );

	function onApplicationStart() {
		application.events = [];
		return true;
	}

	function onSessionStart() {
		cflock( name="ldev6289-short-events", timeout=5 ) {
			arrayAppend( application.events, {
				"ts": dateTimeFormat( now(), "iso" ),
				"kind": "onSessionStart",
				"sessionId": session?.sessionid ?: "(no sessionid)",
				"user": session?.user ?: "(undefined)"
			});
		}
	}

	function onSessionEnd( sessionScope, applicationScope ) {
		cflock( name="ldev6289-short-events", timeout=5 ) {
			arrayAppend( arguments.applicationScope.events, {
				"ts": dateTimeFormat( now(), "iso" ),
				"kind": "onSessionEnd",
				"sessionId": arguments.sessionScope?.sessionid ?: "(no sessionid)",
				"user": arguments.sessionScope?.user ?: "(undefined)"
			});
		}
	}

	function onRequestStart( required string targetPage ) {
		return true;
	}
}
