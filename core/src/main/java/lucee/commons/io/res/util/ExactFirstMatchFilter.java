package lucee.commons.io.res.util;

import lucee.commons.io.res.Resource;
import lucee.commons.io.res.filter.ResourceNameFilter;

/**
 * Case-insensitive filter that matches only the first occurrence.
 * After the first match, all subsequent accept() calls return false.
 */
public final class ExactFirstMatchFilter implements ResourceNameFilter {

	private final String name;
	private boolean found = false;

	public ExactFirstMatchFilter(String name) {
		this.name = name == null ? "" : name;
	}

	@Override
	public boolean accept(Resource parent, String name) {
		if (found) return false;
		if (this.name.equalsIgnoreCase(name)) {
			found = true;
			return true;
		}
		return false;
	}
}
