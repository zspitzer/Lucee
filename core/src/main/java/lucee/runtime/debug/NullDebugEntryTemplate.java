package lucee.runtime.debug;

/**
 * Null Object implementation of DebugEntryTemplate for zero overhead.
 */
public final class NullDebugEntryTemplate implements DebugEntryTemplate {

	public static final NullDebugEntryTemplate INSTANCE = new NullDebugEntryTemplate();

	private NullDebugEntryTemplate() {}

	@Override
	public long getFileLoadTime() {
		return 0;
	}

	@Override
	public void updateFileLoadTime( long fileLoadTime ) {}

	@Override
	public long getQueryTime() {
		return 0;
	}

	@Override
	public void updateQueryTime( long queryTime ) {}

	@Override
	public void resetQueryTime() {}

	@Override
	public long getExeTime() {
		return 0;
	}

	@Override
	public void updateExeTime( long exeTime ) {}

	@Override
	public String getSrc() {
		return "";
	}

	@Override
	public int getCount() {
		return 0;
	}

	@Override
	public long getMin() {
		return 0;
	}

	@Override
	public long getMax() {
		return 0;
	}

	@Override
	public String getPath() {
		return "";
	}

	@Override
	public String getId() {
		return "";
	}
}
