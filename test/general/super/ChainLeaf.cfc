component extends="ChainMid" {

	function trace() {
		return "leaf>" & super.trace();
	}

}