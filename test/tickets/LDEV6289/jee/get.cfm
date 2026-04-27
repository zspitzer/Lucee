<cfscript>
pc = getPageContext();
hs = pc.getRequest().getSession( false );

result = {
	"action": "get",
	"cfid": cfid,
	"sessionUser": session?.user ?: "(undefined)",
	"httpSessionId": isNull( hs ) ? "(no HttpSession)" : hs.getId()
};

content type="application/json";
writeOutput( serializeJson( result ) );
</cfscript>
