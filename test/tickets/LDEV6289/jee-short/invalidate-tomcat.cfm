<cfscript>
// Force HttpSession.invalidate() — simulates Tomcat shutdown / app reload
// / passivation failure / container session-fixation policy. Lucee's CFID
// cookie is untouched. Next session-touching request triggers the silent
// JSession replacement bug in ScopeContext.createNewJSession.
pc = getPageContext();
hs = pc.getRequest().getSession( false );
if ( !isNull( hs ) ) hs.invalidate();
content type="application/json";
writeOutput( '{"invalidated":true}' );
</cfscript>
