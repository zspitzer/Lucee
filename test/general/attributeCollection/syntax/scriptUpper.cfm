<cfscript>
	DIRECTORY ACTION="list" DIRECTORY=request.attrcollDir FILTER="*.cfm" NAME="qLit";
	DIRECTORY ATTRIBUTECOLLECTION=request.attrcollArgs;
	request.attrcollSyntax = { literal: listSort( valueList( qLit.name ), "textnocase" ), coll: listSort( valueList( request.attrcollQ.name ), "textnocase" ) };
</cfscript>