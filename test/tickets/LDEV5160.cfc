component extends="org.lucee.cfml.test.LuceeTestCase" labels="date" {

	function testDateToString(){
        var s = getTickCount("micro");
        var rounds = 1000000;
        loop times="#rounds#" {
            now().toString();
        }
        
        systemOutput(" date.toString(), #numberformat(rounds)# took #numberformat((getTickCount("micro")-s)/1000)# ms ", true);
	}

}

