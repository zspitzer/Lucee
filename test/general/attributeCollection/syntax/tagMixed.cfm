<cfDirectory Action="list" Directory="#request.attrcollDir#" Filter="*.cfm" Name="qLit">
<cfDirectory attributeCollection="#request.attrcollArgs#">
<cfset request.attrcollSyntax = { literal: listSort( valueList( qLit.name ), "textnocase" ), coll: listSort( valueList( request.attrcollQ.name ), "textnocase" ) }>