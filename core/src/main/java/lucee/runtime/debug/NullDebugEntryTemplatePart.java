package lucee.runtime.debug;

/**
 * Null Object implementation of DebugEntryTemplatePart for zero overhead.
 */
public final class NullDebugEntryTemplatePart implements DebugEntryTemplatePart {

	public static final NullDebugEntryTemplatePart INSTANCE = new NullDebugEntryTemplatePart();

	private NullDebugEntryTemplatePart() {}

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

	@Override
	public int getStartPosition() {
		return 0;
	}

	@Override
	public int getEndPosition() {
		return 0;
	}

	@Override
	public int getStartLine() {
		return 0;
	}

	@Override
	public int getEndLine() {
		return 0;
	}

	@Override
	public String getSnippet() {
		return "";
	}
}
