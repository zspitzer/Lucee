<CFDIRECTORY ACTION="list" DIRECTORY="#request.attrcollDir#" FILTER="*.cfm" NAME="qLit">
<CFDIRECTORY ATTRIBUTECOLLECTION="#request.attrcollArgs#">
<cfset request.attrcollSyntax = { literal: listSort( valueList( qLit.name ), "textnocase" ), coll: listSort( valueList( request.attrcollQ.name ), "textnocase" ) }>