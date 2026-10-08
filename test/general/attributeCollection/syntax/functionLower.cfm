<cfscript>
	cfdirectory( action="list", directory=request.attrcollDir, filter="*.cfm", name="qLit" );
	cfdirectory( attributeCollection=request.attrcollArgs );
	request.attrcollSyntax = { literal: listSort( valueList( qLit.name ), "textnocase" ), coll: listSort( valueList( request.attrcollQ.name ), "textnocase" ) };
</cfscript>