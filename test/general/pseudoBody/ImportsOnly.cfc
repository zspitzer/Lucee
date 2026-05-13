// cfimport + functions only. Imports resolve at compile time and the ImportDefintion[] is class-level.
// No per-instance work to redo. False bucket.
component {

	import java.util.HashMap;
	import java.util.LinkedHashMap;

	function makeMap() {
		return new HashMap();
	}

	function makeLinkedMap() {
		return new LinkedHashMap();
	}
}
