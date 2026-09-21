component extends="ChainTop" {

	function trace() {
		return "mid>" & super.trace();
	}

	function traceTag() {
		return super.whoAmI();
	}

}