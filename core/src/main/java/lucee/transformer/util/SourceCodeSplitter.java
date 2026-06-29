package lucee.transformer.util;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Pass 1 region detector for CFML source files.
 *
 * Walks raw source text identifying TAG_MODE vs SCRIPT_MODE vs TAG_ISLAND spans.
 * Runs before charClass encoding so passes 2+3 only operate on executable content.
 *
 * Uses lcText[] for case-insensitive tag matching (same package as SourceCode).
 *
 * See lucee-projects/compiler/cfml-region-aware-compiler.md
 */
public class SourceCodeSplitter {

	public enum RegionKind {
		TAG_MODE,
		SCRIPT_MODE,
		TAG_ISLAND,     // ``` ... ``` inside script
		COMMENT_LINE,   // // ... \n  (SCRIPT_MODE only)
		COMMENT_BLOCK,  // /* ... */  (SCRIPT_MODE only)
		COMMENT_CFML,   // <!--- ... ---> (TAG_MODE / TAG_ISLAND only)
		WHITESPACE,     // contiguous [ \t\r\n]+ spans (any non-comment region)
		CF_TAG,          // <cf...> / </cf...> tag markup (TAG_MODE only)
		HTML,            // static HTML text / tags (TAG_MODE only, outside cfoutput)
		CFOUTPUT,        // static text inside <cfoutput>...</cfoutput>
		CFOUTPUT_EXPR    // #expr# interpolation inside cfoutput
	}

	public static final class Region {
		public final int        start;
		public final int        end;
		public final RegionKind kind;

		Region( int start, int end, RegionKind kind ) {
			this.start = start;
			this.end   = end;
			this.kind  = kind;
		}

		@Override
		public String toString() {
			return String.format( "%-8d %-8d %s", start, end, kind );
		}
	}

	// extracted from CFMLTransformer.tag() / AbstrCFMLScriptTransformer.islandStatement()
	private static final char[] CFSCRIPT_OPEN  = "<cfscript".toCharArray();
	private static final char[] CFSCRIPT_CLOSE = "</cfscript>".toCharArray();
	private static final char[] TAG_ISLAND_SEQ = "```".toCharArray();

	// TODO: wire from TagLibTag.getParseBody() when integrating with the compiler pipeline
	private static final Set<String> DEFAULT_PARSE_BODY_TAGS = new HashSet<>( Arrays.asList( "cfoutput", "cfquery" ) );

	private final char[]      text;
	private final char[]      lc;
	private final Set<String> parseBodyTags;

	public SourceCodeSplitter( SourceCode src ) {
		this( src, DEFAULT_PARSE_BODY_TAGS );
	}

	public SourceCodeSplitter( SourceCode src, Set<String> parseBodyTags ) {
		this.text          = src.text;
		this.lc            = src.lcText;
		this.parseBodyTags = parseBodyTags;
	}

	public List<Region> detect( boolean scriptFile ) {
		List<Region> regions = new ArrayList<>();
		int len = text.length;
		int i   = 0;

		if ( scriptFile ) {
			// .cfc — entire file is SCRIPT_MODE, but may contain tag islands
			regions.add( new Region( 0, len, RegionKind.SCRIPT_MODE ) );
			return extractComments( expandIslands( regions ) );
		}

		// .cfm — starts in TAG_MODE, look for <cfscript> boundaries
		int regionStart = 0;

		while ( i < len ) {
			// skip cfml comments so <!--- "<cfscript>" ---> doesn't trigger
			if ( matchAt( lc, i, "<!---".toCharArray() ) ) {
				i = skipCfmlComment( i );
				continue;
			}

			// skip quoted strings so "<cfscript>" attribute values don't trigger
			if ( text[ i ] == '"' || text[ i ] == '\'' ) {
				i = skipString( i );
				continue;
			}

			// look for <cfscript> opening — extracted from CFMLTransformer tag dispatch
			if ( matchAt( lc, i, CFSCRIPT_OPEN ) ) {
				int tagStart = i;
				int j = i + CFSCRIPT_OPEN.length;
				// skip optional whitespace and attributes to find closing >
				while ( j < len && text[ j ] != '>' ) j++;
				if ( j < len ) {
					// emit preceding TAG_MODE span
					if ( tagStart > regionStart )
						regions.add( new Region( regionStart, tagStart, RegionKind.TAG_MODE ) );

					int scriptStart = j + 1; // char after >
					// scan for </cfscript>, skipping strings so "</cfscript>" doesn't trigger
					int scriptEnd = findCloseSkipStrings( scriptStart, CFSCRIPT_CLOSE );
					if ( scriptEnd == -1 ) scriptEnd = len; // unclosed — treat rest as script

					regions.add( new Region( scriptStart, scriptEnd, RegionKind.SCRIPT_MODE ) );
					i = scriptEnd + CFSCRIPT_CLOSE.length;
					regionStart = i;
					continue;
				}
			}

			i++;
		}

		// trailing TAG_MODE span
		if ( regionStart < len )
			regions.add( new Region( regionStart, len, RegionKind.TAG_MODE ) );

		// expand SCRIPT_MODE regions to find TAG_ISLANDs inside them, then extract comments
		return extractComments( expandIslands( regions ) );
	}

	/**
	 * Walk SCRIPT_MODE regions looking for ``` tag islands.
	 * extracted from AbstrCFMLScriptTransformer.islandStatement()
	 */
	private List<Region> expandIslands( List<Region> input ) {
		List<Region> out = new ArrayList<>( input.size() );
		for ( Region r : input ) {
			if ( r.kind != RegionKind.SCRIPT_MODE ) {
				out.add( r );
				continue;
			}
			int i     = r.start;
			int start = r.start;
			while ( i < r.end - 2 ) {
				// skip strings inside script so "```" doesn't trigger
				if ( text[ i ] == '"' || text[ i ] == '\'' ) {
					i = skipString( i );
					continue;
				}
				// skip // line comments so ``` inside them doesn't trigger
				if ( text[ i ] == '/' && i + 1 < r.end && text[ i + 1 ] == '/' ) {
					while ( i < r.end && text[ i ] != '\n' && text[ i ] != '\r' ) i++;
					continue;
				}
				// skip /* block comments */ so ``` inside them doesn't trigger
				if ( text[ i ] == '/' && i + 1 < r.end && text[ i + 1 ] == '*' ) {
					i += 2;
					while ( i + 1 < r.end && !( text[ i ] == '*' && text[ i + 1 ] == '/' ) ) i++;
					if ( i + 1 < r.end ) i += 2;
					continue;
				}
				if ( matchAt( text, i, TAG_ISLAND_SEQ ) ) {
					if ( i > start )
						out.add( new Region( start, i, RegionKind.SCRIPT_MODE ) );
					int islandStart = i + TAG_ISLAND_SEQ.length;
					int islandEnd   = findCloseSkipStrings( islandStart, TAG_ISLAND_SEQ );
					if ( islandEnd == -1 ) islandEnd = r.end;
					out.add( new Region( islandStart, islandEnd, RegionKind.TAG_ISLAND ) );
					i     = islandEnd + TAG_ISLAND_SEQ.length;
					start = i;
					continue;
				}
				i++;
			}
			if ( start < r.end )
				out.add( new Region( start, r.end, RegionKind.SCRIPT_MODE ) );
		}
		return out;
	}

	/**
	 * Walk each region and split out comment sub-regions.
	 * SCRIPT_MODE: // line comments, /* block comments (skip strings)
	 * TAG_MODE / TAG_ISLAND: <!--- ---> cfml comments (with nesting)
	 */
	private List<Region> extractComments( List<Region> input ) {
		List<Region> out = new ArrayList<>( input.size() + 16 );
		for ( Region r : input ) {
			if ( r.kind == RegionKind.SCRIPT_MODE ) {
				extractScriptComments( out, r );
			}
			else if ( r.kind == RegionKind.TAG_MODE || r.kind == RegionKind.TAG_ISLAND ) {
				extractCfmlComments( out, r );
			}
			else {
				out.add( r );
			}
		}
		return extractWhitespace( extractCfoutput( extractCfTags( out ) ) );
	}

	/** Split contiguous whitespace runs out of any non-comment region. */
	private List<Region> extractWhitespace( List<Region> input ) {
		List<Region> out = new ArrayList<>( input.size() + 32 );
		for ( Region r : input ) {
			// leave comment regions intact — whitespace inside them stays as-is
			if ( r.kind == RegionKind.COMMENT_LINE || r.kind == RegionKind.COMMENT_BLOCK || r.kind == RegionKind.COMMENT_CFML ) {
				out.add( r );
				continue;
			}
			int i     = r.start;
			int start = r.start;
			while ( i < r.end ) {
				char c = text[ i ];
				if ( c == ' ' || c == '\t' || c == '\r' || c == '\n' ) {
					if ( i > start ) out.add( new Region( start, i, r.kind ) );
					int wsStart = i;
					while ( i < r.end && ( text[ i ] == ' ' || text[ i ] == '\t' || text[ i ] == '\r' || text[ i ] == '\n' ) ) i++;
					out.add( new Region( wsStart, i, RegionKind.WHITESPACE ) );
					start = i;
				}
				else {
					i++;
				}
			}
			if ( start < r.end ) out.add( new Region( start, r.end, r.kind ) );
		}
		return out;
	}

	/**
	 * Within TAG_MODE non-whitespace regions, split <cf...> tag markup from plain HTML.
	 * A CF_TAG span covers the entire tag from < to closing >, skipping quoted attributes.
	 * Everything else becomes HTML.
	 */
	private List<Region> extractCfTags( List<Region> input ) {
		List<Region> out = new ArrayList<>( input.size() + 16 );
		for ( Region r : input ) {
			if ( r.kind != RegionKind.TAG_MODE ) {
				out.add( r );
				continue;
			}
			int i     = r.start;
			int start = r.start;
			while ( i < r.end ) {
				// look for <cf or </cf (case-insensitive via lc[])
				if ( text[ i ] == '<' && i + 2 < r.end
						&& ( ( lc[ i + 1 ] == 'c' && lc[ i + 2 ] == 'f' )                          // <cf...
						||   ( text[ i + 1 ] == '/' && i + 3 < r.end && lc[ i + 2 ] == 'c' && lc[ i + 3 ] == 'f' ) ) ) { // </cf...
					if ( i > start ) out.add( new Region( start, i, RegionKind.HTML ) );
					int tagStart = i;
					i += ( text[ i + 1 ] == '/' ) ? 4 : 3; // skip past </cf or <cf
					// scan to closing >, skipping quoted attribute values
					while ( i < r.end && text[ i ] != '>' ) {
						if ( text[ i ] == '"' || text[ i ] == '\'' ) i = skipString( i );
						else i++;
					}
					if ( i < r.end ) i++; // include >
					out.add( new Region( tagStart, i, RegionKind.CF_TAG ) );
					start = i;
				}
				else {
					i++;
				}
			}
			if ( start < r.end ) out.add( new Region( start, r.end, RegionKind.HTML ) );
		}
		return out;
	}

	/**
	 * Track parseBody-tag nesting via CF_TAG regions, then scan HTML content inside
	 * for #expr# interpolations (## = escaped literal #).
	 * Which tags qualify is driven by parseBodyTags (see TODO on constructor).
	 */
	private List<Region> extractCfoutput( List<Region> input ) {
		List<Region> out   = new ArrayList<>( input.size() + 16 );
		int          depth = 0;
		for ( Region r : input ) {
			if ( r.kind == RegionKind.CF_TAG ) {
				// re-scan tag name — extract lowercase name between < (and optional /) and first non-alpha
				int nameStart = r.start + 1; // past <
				boolean closing = text[ nameStart ] == '/';
				if ( closing ) nameStart++; // past /
				int nameEnd = nameStart;
				while ( nameEnd < r.end && Character.isLetter( text[ nameEnd ] ) ) nameEnd++;
				String tagName = new String( lc, nameStart, nameEnd - nameStart );
				if ( parseBodyTags.contains( tagName ) ) {
					if ( closing ) depth--;
					else depth++;
				}
				out.add( r );
			}
			else if ( depth > 0 && ( r.kind == RegionKind.HTML || r.kind == RegionKind.TAG_MODE ) ) {
				extractCfoutputExprs( out, r );
			}
			else {
				out.add( r );
			}
		}
		return out;
	}

	/** Split #expr# interpolations out of a cfoutput body region. ## = escaped #, stays as CFOUTPUT. */
	private void extractCfoutputExprs( List<Region> out, Region r ) {
		int i     = r.start;
		int start = r.start;
		while ( i < r.end ) {
			if ( text[ i ] == '#' ) {
				// ## = escaped hash — emit accumulated content + the single # and skip both
				if ( i + 1 < r.end && text[ i + 1 ] == '#' ) {
					i += 2;
					continue;
				}
				// #expr# — emit preceding static content, then the expression span
				if ( i > start ) out.add( new Region( start, i, RegionKind.CFOUTPUT ) );
				int exprStart = i + 1; // past opening #
				i = exprStart;
				// scan to closing # — strings are skipped wholesale; ## escaping is content-level only
				while ( i < r.end ) {
					if ( text[ i ] == '"' || text[ i ] == '\'' ) {
						i = skipStringBounded( i, r.end );
					}
					else if ( text[ i ] == '#' ) {
						break; // closing #
					}
					else i++;
				}
				out.add( new Region( exprStart, i, RegionKind.CFOUTPUT_EXPR ) );
				if ( i < r.end ) i++; // past closing #
				start = i;
			}
			else {
				i++;
			}
		}
		if ( start < r.end ) out.add( new Region( start, r.end, RegionKind.CFOUTPUT ) );
	}

	private void extractScriptComments( List<Region> out, Region r ) {
		int i     = r.start;
		int start = r.start;
		while ( i < r.end ) {
			if ( text[ i ] == '"' || text[ i ] == '\'' ) {
				i = skipString( i );
				continue;
			}
			// // line comment
			if ( i + 1 < r.end && text[ i ] == '/' && text[ i + 1 ] == '/' ) {
				if ( i > start ) out.add( new Region( start, i, RegionKind.SCRIPT_MODE ) );
				int commentStart = i;
				i += 2;
				while ( i < r.end && text[ i ] != '\n' && text[ i ] != '\r' ) i++;
				if ( i < r.end ) i++; // include the line ending
				out.add( new Region( commentStart, i, RegionKind.COMMENT_LINE ) );
				start = i;
				continue;
			}
			// /* block comment */
			if ( i + 1 < r.end && text[ i ] == '/' && text[ i + 1 ] == '*' ) {
				if ( i > start ) out.add( new Region( start, i, RegionKind.SCRIPT_MODE ) );
				int commentStart = i;
				i += 2;
				while ( i + 1 < r.end && !( text[ i ] == '*' && text[ i + 1 ] == '/' ) ) i++;
				i += 2; // past */
				if ( i > r.end ) i = r.end;
				out.add( new Region( commentStart, i, RegionKind.COMMENT_BLOCK ) );
				start = i;
				continue;
			}
			i++;
		}
		if ( start < r.end ) out.add( new Region( start, r.end, RegionKind.SCRIPT_MODE ) );
	}

	private void extractCfmlComments( List<Region> out, Region r ) {
		int i     = r.start;
		int start = r.start;
		while ( i < r.end ) {
			// skip quoted attribute values so <!--- inside output="<!--- -->" doesn't trigger
			if ( text[ i ] == '"' || text[ i ] == '\'' ) {
				i = skipStringBounded( i, r.end );
				continue;
			}
			if ( matchAt( lc, i, "<!---".toCharArray() ) ) {
				if ( i > start ) out.add( new Region( start, i, r.kind ) );
				int commentStart = i;
				i += 5;
				int depth = 1;
				while ( i < r.end && depth > 0 ) {
					if ( matchAt( lc, i, "<!---".toCharArray() ) ) { depth++; i += 5; }
					else if ( matchAt( text, i, "--->".toCharArray() ) ) { depth--; i += 4; }
					else i++;
				}
				out.add( new Region( commentStart, i, RegionKind.COMMENT_CFML ) );
				start = i;
				continue;
			}
			i++;
		}
		if ( start < r.end ) out.add( new Region( start, r.end, r.kind ) );
	}

	private boolean matchAt( char[] src, int pos, char[] seq ) {
		if ( pos + seq.length > src.length ) return false;
		for ( int k = 0; k < seq.length; k++ ) {
			if ( src[ pos + k ] != seq[ k ] ) return false;
		}
		return true;
	}

	/** skip past a quoted string bounded by limit — won't escape past a region boundary */
	private int skipStringBounded( int pos, int limit ) {
		char q = text[ pos++ ];
		while ( pos < limit ) {
			if ( text[ pos ] == q ) {
				if ( pos + 1 < limit && text[ pos + 1 ] == q ) {
					pos += 2;
				}
				else {
					pos++;
					break;
				}
			}
			else pos++;
		}
		return pos;
	}

	/**
	 * Skip past a CFML string literal, handling:
	 *  - doubled-quote escapes ("" or '')
	 *  - ## escaped hash
	 *  - #expr# interpolations (which may contain nested strings with their own quotes)
	 */
	private int skipString( int pos ) {
		char q = text[ pos++ ];
		while ( pos < text.length ) {
			char c = text[ pos ];
			if ( c == q ) {
				if ( pos + 1 < text.length && text[ pos + 1 ] == q ) {
					pos += 2; // doubled-quote escape
				}
				else {
					pos++; // closing quote
					break;
				}
			}
			else if ( c == '#' ) {
				if ( pos + 1 < text.length && text[ pos + 1 ] == '#' ) {
					pos += 2; // ## = escaped hash
				}
				else {
					pos++; // past opening #
					// skip CFML interpolation — scan until matching #, handling nested strings
					while ( pos < text.length && text[ pos ] != '#' ) {
						if ( text[ pos ] == '"' || text[ pos ] == '\'' ) {
							pos = skipString( pos ); // nested string
						}
						else pos++;
					}
					if ( pos < text.length ) pos++; // past closing #
				}
			}
			else pos++;
		}
		return pos;
	}

	/** skip past a <!--- ---> cfml comment (with nesting) */
	private int skipCfmlComment( int pos ) {
		pos += 5; // past <!---
		int depth = 1;
		while ( pos < text.length && depth > 0 ) {
			if ( matchAt( lc, pos, "<!---".toCharArray() ) ) { depth++; pos += 5; }
			else if ( matchAt( text, pos, "--->".toCharArray() ) ) { depth--; pos += 4; }
			else pos++;
		}
		return pos;
	}

	/** find the next occurrence of seq, skipping string literals */
	private int findCloseSkipStrings( int pos, char[] seq ) {
		int limit = text.length - seq.length;
		while ( pos <= limit ) {
			if ( text[ pos ] == '"' || text[ pos ] == '\'' ) {
				pos = skipString( pos );
				continue;
			}
			if ( matchAt( lc, pos, seq ) ) return pos;
			pos++;
		}
		return -1;
	}

	/**
	 * Human-readable region dump for corpus validation.
	 * Format: start(8) end(8) KIND
	 */
	public static String dump( List<Region> regions ) {
		StringBuilder sb = new StringBuilder();
		sb.append( String.format( "%-8s %-8s %s%n", "START", "END", "KIND" ) );
		for ( Region r : regions ) sb.append( r ).append( '\n' );
		return sb.toString();
	}

	/**
	 * Region dump with source text snippets — shows first 40 chars of each region for debugging.
	 */
	public static String dump( List<Region> regions, char[] text ) {
		StringBuilder sb = new StringBuilder();
		sb.append( String.format( "%-8s %-8s %-20s %s%n", "START", "END", "KIND", "TEXT" ) );
		for ( Region r : regions ) {
			int len     = Math.min( 40, r.end - r.start );
			String snip = new String( text, r.start, len ).replace( "\n", "\\n" ).replace( "\r", "\\r" ).replace( "\t", "\\t" );
			if ( r.end - r.start > 40 ) snip += "…";
			sb.append( String.format( "%-8d %-8d %-20s %s%n", r.start, r.end, r.kind, snip ) );
		}
		return sb.toString();
	}
}
