component extends="org.lucee.cfml.test.LuceeTestCase" {

	function testToString() {
        var st = QueryRowData( ExtensionList(), 1 );
        var arr = [];
        loop times="100"{
            ArrayAppend(arr, duplicate( st ) );
        }
        loop times="100"{
            arr.toString();
        }
	}
}