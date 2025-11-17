package lucee.runtime.debug;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import lucee.runtime.PageContext;
import lucee.runtime.PageSource;
import lucee.runtime.config.Config;
import lucee.runtime.db.SQL;
import lucee.runtime.exp.CatchBlock;
import lucee.runtime.exp.PageException;
import lucee.runtime.type.Query;
import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;

/**
 * Null Object implementation of Debugger for zero overhead in production mode.
 * All methods are no-ops, allowing JIT dead code elimination.
 */
public final class NullDebugger implements Debugger {

	public static final NullDebugger INSTANCE = new NullDebugger();

	private NullDebugger() {}

	@Override
	public void init( Config config ) {}

	@Override
	public void reset() {}

	@Override
	public DebugEntryTemplate getEntry( PageContext pc, PageSource source ) {
		return NullDebugEntryTemplate.INSTANCE;
	}

	@Override
	public DebugEntryTemplate getEntry( PageContext pc, PageSource source, String key ) {
		return NullDebugEntryTemplate.INSTANCE;
	}

	@Override
	public DebugEntryTemplatePart getEntry( PageContext pc, PageSource source, int startPos, int endPos ) {
		return NullDebugEntryTemplatePart.INSTANCE;
	}

	@Override
	public void setOutput( boolean output ) {}

	@Override
	public List<QueryEntry> getQueries() {
		return Collections.emptyList();
	}

	@Override
	public void writeOut( PageContext pc ) throws IOException {}

	@Override
	public Struct getDebuggingData( PageContext pc ) throws PageException {
		return new StructImpl();
	}

	@Override
	public Struct getDebuggingData( PageContext pc, boolean addAddionalInfo ) throws PageException {
		return new StructImpl();
	}

	@Override
	@Deprecated
	public DebugTimer addTimer( String label, long exe, String template ) {
		return null;
	}

	@Override
	public DebugTimer addTimer( String label, long exe, String template, int line ) {
		return null;
	}

	@Override
	public DebugTrace addTrace( int type, String category, String text, PageSource page, String varName, String varValue ) {
		return null;
	}

	@Override
	public DebugTrace addTrace( int type, String category, String text, String template, int line, String action, String varName, String varValue ) {
		return null;
	}

	@Override
	public DebugTrace[] getTraces() {
		return new DebugTrace[0];
	}

	@Override
	public void addException( Config config, PageException pe ) {}

	@Override
	public CatchBlock[] getExceptions() {
		return new CatchBlock[0];
	}

	@Override
	@Deprecated
	public void addImplicitAccess( String scope, String name ) {}

	@Override
	public void addImplicitAccess( PageContext pc, String scope, String name ) {}

	@Override
	public ImplicitAccess[] getImplicitAccesses( int scope, String name ) {
		return new ImplicitAccess[0];
	}

	@Override
	@Deprecated
	public void addQuery( Query query, String datasource, String name, SQL sql, int recordcount, PageSource src, int time ) {}

	@Override
	public void addQuery( Query query, String datasource, String name, SQL sql, int recordcount, PageSource src, long time ) {}

	@Override
	public DebugTrace[] getTraces( PageContext pc ) {
		return new DebugTrace[0];
	}

	@Override
	public void addGenericData( String labelCategory, Map<String, String> data ) {}

	@Override
	public Map<String, Map<String, List<String>>> getGenericData() {
		return Collections.emptyMap();
	}

	@Override
	public DebugDump addDump( PageSource ps, String dump ) {
		return null;
	}

	@Override
	public void setOutputLog( DebugOutputLog outputLog ) {}
}
