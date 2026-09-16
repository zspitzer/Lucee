package lucee.runtime.type;

import lucee.runtime.PageContext;

/**
 * A collection that binds a shared accessor flyweight to its instance on the way out of
 * {@code get}. {@code peek} returns the stored value as is, for a caller that invokes or probes
 * it in place and never lets it escape, so no {@link BoundUDF} is allocated for a value that is
 * dropped straight after.
 */
public interface Peekable {

	public Object peek(PageContext pc, Collection.Key key, Object defaultValue);
}
