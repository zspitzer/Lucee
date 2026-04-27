component {
	this.name = "ldev6289-jee";
	this.sessionManagement = true;
	this.setClientCookies = true;
	this.sessionType = "jee";
	this.sessionStorage = "memory";
	this.sessionTimeout = createTimespan( 0, 0, 30, 0 );
	this.applicationTimeout = createTimespan( 0, 1, 0, 0 );

	function onApplicationStart() {
		application.events = [];
		return true;
	}

	function onSessionStart() {
		cflock( name="ldev6289-events", timeout=5 ) {
			arrayAppend( application.events, {
				"ts": dateTimeFormat( now(), "iso" ),
				"kind": "onSessionStart",
				"sessionId": session?.sessionid ?: "(no sessionid)",
				"user": session?.user ?: "(undefined)"
			});
		}
	}

	function onSessionEnd( sessionScope, applicationScope ) {
		cflock( name="ldev6289-events", timeout=5 ) {
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
