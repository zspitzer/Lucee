<cfscript>
// Benchmark for LDEV-3335: accessor performance and memory usage
// Run with JFR enabled to profile memory allocation and method calls
// JFR will capture actual object allocations and sizes

function runBenchmark( componentType, iterations=50000 ) {
	var startTime = getTickCount();
	var components = [];

	systemOutput( "Starting benchmark for: #componentType#", true );
	systemOutput( "Creating #iterations# instances...", true );

	// Create instances - JFR will track allocations
	for ( var i = 1; i <= iterations; i++ ) {
		switch( componentType ) {
			case "noAccessors":
				components[ i ] = new testNoAccessors();
				break;
			case "manual":
				components[ i ] = new testMannual();
				break;
			case "withAccessors":
				components[ i ] = new testWithAccessors();
				break;
		}
	}

	var endTime = getTickCount();
	var duration = endTime - startTime;

	systemOutput( "Results for #componentType#:", true );
	systemOutput( "  - Creation time: #duration#ms", true );
	systemOutput( "  - Instances per second: #numberFormat( iterations / (duration/1000) )#", true );
	systemOutput( "  - Array size: #arrayLen( components )# instances", true );
	systemOutput( "", true );

	return {
		type: componentType,
		iterations: iterations,
		duration: duration,
		instancesPerSec: iterations / (duration/1000)
	};
}

// Run benchmarks
systemOutput( "=".repeat( 80 ), true );
systemOutput( "LDEV-3335 Accessor Performance Benchmark", true );
systemOutput( "=".repeat( 80 ), true );
systemOutput( "", true );

// Warmup round to ensure JIT compilation
systemOutput( "Running warmup round (30,000 instances)...", true );
runBenchmark( "noAccessors", 30000 );
runBenchmark( "manual", 30000 );
runBenchmark( "withAccessors", 30000 );
systemOutput( "Warmup complete!", true );
systemOutput( "", true );

// Actual benchmark runs
results = [];
results[ 1 ] = runBenchmark( "noAccessors" );
results[ 2 ] = runBenchmark( "manual" );
results[ 3 ] = runBenchmark( "withAccessors" );

// Summary comparison
systemOutput( "=".repeat( 80 ), true );
systemOutput( "SUMMARY COMPARISON", true );
systemOutput( "=".repeat( 80 ), true );

baseline = results[ 1 ];

for ( result in results ) {
	timeOverhead = ((result.duration - baseline.duration) / baseline.duration) * 100;

	systemOutput( "#result.type#:", true );
	systemOutput( "  Time overhead vs baseline: #numberFormat( timeOverhead, "0.00" )#%", true );
	systemOutput( "", true );
}

// Memory info
runtime = createObject( "java", "java.lang.Runtime" ).getRuntime();
systemOutput( "JVM Memory Info:", true );
systemOutput( "  Total Memory: #numberFormat( runtime.totalMemory() / 1024 / 1024 )# MB", true );
systemOutput( "  Free Memory: #numberFormat( runtime.freeMemory() / 1024 / 1024 )# MB", true );
systemOutput( "  Used Memory: #numberFormat( (runtime.totalMemory() - runtime.freeMemory()) / 1024 / 1024 )# MB", true );
systemOutput( "  Max Memory: #numberFormat( runtime.maxMemory() / 1024 / 1024 )# MB", true );
systemOutput( "", true );

systemOutput( "Benchmark complete!", true );
</cfscript>
