<cfscript>

// TRANSACTION_NONE = 0, TRANSACTION_READ_COMMITTED = 2, TRANSACTION_SERIALIZABLE = 8

// Test: ORM usage in a transaction should not change the DSM's isolation field
// The bug was that _add() forced isolation to SERIALIZABLE, which then leaked
// to any regular JDBC connections obtained in the same transaction

transaction {
	// check isolation field before ORM
	pc = getPageContext();
	dsm = pc.getDataSourceManager();
	field = dsm.getClass().getDeclaredField( "isolation" );
	field.setAccessible( true );
	beforeORM = field.get( dsm );

	entityLoad( "TestEntity" );

	// check isolation field after ORM
	afterORM = field.get( dsm );
}

// isolation field should be TRANSACTION_NONE (0) both before and after ORM usage
// when no explicit isolation was specified on the transaction
echo( "before=#beforeORM#" );
echo( ":" );
echo( "after=#afterORM#" );
echo( ":" );
echo( beforeORM == afterORM );

</cfscript>
