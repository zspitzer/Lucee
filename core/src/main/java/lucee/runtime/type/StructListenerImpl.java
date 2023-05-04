package lucee.runtime.type;

import lucee.runtime.PageContext;
import lucee.runtime.dump.DumpData;
import lucee.runtime.dump.DumpProperties;
import lucee.runtime.dump.DumpTable;
import lucee.runtime.engine.ThreadLocalPageContext;
import lucee.runtime.exp.PageException;

public class StructListenerImpl extends StructImpl {

	private static final long serialVersionUID = -2286369022408510584L;

	private UDF udf;

	public StructListenerImpl(int type, UDF udf) {
		super(type);
		this.udf = udf;
	}

	@Override
	public Object get(Key key, Object defaultValue) {
		Object res = super.get(key, NULL);
		if (res == NULL) return onMissingKey(null, key, defaultValue);
		else onKey(null, key, null);
		return res;
	}

	@Override
	public Object g(Key key, Object defaultValue) {
		Object res = super.g(key, NULL);
		if (res == NULL) return onMissingKey(null, key, defaultValue);
		else onKey(null, key, null);
		return res;
	}

	@Override
	public Object get(PageContext pc, Key key, Object defaultValue) {
		Object res = super.get(pc, key, NULL);
		if (res == NULL) return onMissingKey(pc, key, defaultValue);
		else onKey(pc, key, null);
		return res;
	}

	@Override
	public Object g(Key key) throws PageException {
		Object res = super.g(key, NULL);
		if (res == NULL) return onMissingKey(null, key);
		else onKey(null, key);
		return res;
	}

	@Override
	public Object get(Key key) throws PageException {
		Object res = super.get(key, NULL);
		if (res == NULL) return onMissingKey(null, key);
		else onKey(null, key);
		return res;
	}

	@Override
	public Object get(PageContext pc, Key key) throws PageException {
		Object res = super.get(pc, key, NULL);
		if (res == NULL) return onMissingKey(pc, key);
		else onKey(pc, key);
		return res;
	}

	@Override
	public DumpData toDumpData(PageContext pageContext, int maxlevel, DumpProperties properties) {
		DumpTable dt = (DumpTable) super.toDumpData(pageContext, maxlevel, properties);
		dt.setComment("this struct has a onMissingKey defined");
		return dt;
	}

	private Object onMissingKey(PageContext pc, Key key) throws PageException {
		pc = ThreadLocalPageContext.get(pc);
		return udf.call(pc, new Object[] { key, this, true }, true);
	}

	private Object onMissingKey(PageContext pc, Key key, Object defaultValue) {
		try {
			return onMissingKey(pc, key);
		}
		catch (Exception e) {
			return defaultValue;
		}
	}

	private void onKey(PageContext pc, Key key) throws PageException {
		pc = ThreadLocalPageContext.get(pc);
		udf.call(pc, new Object[] { key, this, false }, true);
	}

	private void onKey(PageContext pc, Key key, Object defaultValue) {
		try {
			onKey(pc, key);
		}
		catch (Exception e) {}
	}
}
