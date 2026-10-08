<cfscript>
	Directory Action="list" Directory=request.attrcollDir Filter="*.cfm" Name="qLit";
	Directory attributeCollection=request.attrcollArgs;
	request.attrcollSyntax = { literal: listSort( valueList( qLit.name ), "textnocase" ), coll: listSort( valueList( request.attrcollQ.name ), "textnocase" ) };
</cfscript>