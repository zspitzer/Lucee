<cfset res = {}>
<cfset dir = request.attrcollDir>
<cfset newDir = dir & "prec-tag-create">
<cfif directoryExists( newDir )><cfset directoryDelete( newDir, true )></cfif>
<cfdirectory action="list" directory="#newDir#" name="q1" attributeCollection="#{ action: "create" }#">
<cfset res.action = { created: directoryExists( newDir ), listed: isDefined( "q1" ) }>
<cfdirectory action="list" directory="#dir#" name="qa" attributeCollection="#{ variable: "qb" }#">
<cfset res.nameVsAlias = { qa: isDefined( "qa" ), qb: isDefined( "qb" ) }>
<cfdirectory action="list" directory="#dir#" variable="qc" attributeCollection="#{ name: "qd" }#">
<cfset res.aliasVsName = { qc: isDefined( "qc" ), qd: isDefined( "qd" ) }>
<cfsavecontent variable="precOut"><cfmodule template="echoAttrs.cfm" a="explicit" attributeCollection="#{ a: "coll", b: "coll" }#"></cfsavecontent>
<cfset res.module = precOut>
<cfset request.attrcollPrecedence = res>