package lucee.transformer.util;

import java.util.Iterator;

import lucee.runtime.type.Struct;
import lucee.runtime.type.StructImpl;
import lucee.runtime.type.util.KeyConstants;
import lucee.transformer.Body;
import lucee.transformer.Node;
import lucee.transformer.Position;
import lucee.transformer.bytecode.statement.tag.TagComponent;
import lucee.transformer.expression.literal.Literal;
import lucee.transformer.statement.Statement;
import lucee.transformer.statement.tag.Tag;

public final class TransformerUtil {
	/**
	 * Gibt ein uebergeordnetes Tag mit dem uebergebenen Full-Name (Namespace und Name) zurueck, falls
	 * ein solches existiert, andernfalls wird null zurueckgegeben.
	 * 
	 * @param stat Startelement, von wo aus gesucht werden soll.
	 * @param fullName Name des gesuchten Tags.
	 * @return uebergeornetes Element oder null.
	 */
	public static Tag getAncestorTag(Statement stat, String fullName) {
		Statement parent = stat;
		Tag tag;
		while (true) {
			parent = parent.getParent();
			if (parent == null) return null;
			if (parent instanceof Tag) {
				tag = (Tag) parent;
				if (tag.getFullname().equalsIgnoreCase(fullName)) return tag;
			}
		}
	}

	public static boolean hasAncestorTag(Statement stat, String fullName) {
		return TransformerUtil.getAncestorTag(stat, fullName) != null;
	}

	public static boolean containsComponent(Body body) {
		if (body == null) return false;

		Iterator<Statement> it = body.getStatements().iterator();
		while (it.hasNext()) {
			if (it.next() instanceof TagComponent) return true;
		}
		return false;
	}

	public static Struct toStruct(Node node) {
		Struct sct = new StructImpl(Struct.TYPE_LINKED);

		// start
		Struct sctStart = new StructImpl(Struct.TYPE_LINKED);
		Position start = node.getStart();
		sctStart.setEL(KeyConstants._line, start.line);
		sctStart.setEL(KeyConstants._column, start.column);
		sctStart.setEL(KeyConstants._offset, start.pos);
		sct.setEL(KeyConstants._start, sctStart);

		// end
		Struct sctEnd = new StructImpl(Struct.TYPE_LINKED);
		Position end = node.getEnd();
		sctEnd.setEL(KeyConstants._line, end.line);
		sctEnd.setEL(KeyConstants._column, end.column);
		sctEnd.setEL(KeyConstants._offset, end.pos);
		sct.setEL(KeyConstants._end, sctEnd);

		// type TODO
		sct.setEL(KeyConstants._type, node.getClass().getName());

		// literal

		if (node instanceof Literal) {
			Literal lit = (Literal) node;
			sct.setEL(KeyConstants._value, lit.getValue());
		}

		return sct;
	}
}
