<cfscript>
	param name="url.chaos";
	param name="url.idx";
	params = {
		name: {value: createUUID(), sqltype="varchar" }
	};

	query params=params result="result" datasource="LDEV5053" {
		echo( "INSERT INTO ldev5053 ( name ) VALUES ( :name )" );
	}
	params.id = { value: result.generatedKey, sqltype="numeric" };

	query name="q" params=params datasource="LDEV5053" {
		echo( "SELECT id, name FROM ldev5053 WHERE name = :name AND id = :id " );
	}

	if( url.chaos && (url.idx mod 3) eq 1)
		throw "chaos mode, throw exception at [#url.idx#]";

	if ( q.id != params.id.value || q.name != params.name.value || q.recordcount !=1 )
		throw "invalid result [#params.toJson()#], [#q.toJson()#]";
</cfscript>