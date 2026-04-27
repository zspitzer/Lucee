<cfscript>
session.user = url.user ?: "zac";
pc = getPageContext();
hs = pc.getRequest().getSession( false );

result = {
	"action": "set",
	"cfid": cfid,
	"sessionUser": session.user,
	"httpSessionId": isNull( hs ) ? "(no HttpSession)" : hs.getId()
};

content type="application/json";
writeOutput( serializeJson( result ) );
</cfscript>
