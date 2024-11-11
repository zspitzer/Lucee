/**
 *
 * Copyright (c) 2014, the Railo Company Ltd. All rights reserved.
 *
 * This library is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either 
 * version 2.1 of the License, or (at your option) any later version.
 * 
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 * 
 * You should have received a copy of the GNU Lesser General Public 
 * License along with this library.  If not, see <http://www.gnu.org/licenses/>.
 * 
 **/
package lucee.runtime.type.scope;

import java.util.concurrent.ConcurrentLinkedQueue;

import lucee.runtime.PageContext;
//import lucee.aprint;

/**
 * creates Local and Argument scopes and recyle it
 */
public final class ScopeFactory {

	private static final int MAX_SIZE = 50;

	int argumentCounter = 0;

	private final ConcurrentLinkedQueue<Argument> arguments = new ConcurrentLinkedQueue<Argument>();
	private final ConcurrentLinkedQueue<Argument> argumentsLarge = new ConcurrentLinkedQueue<Argument>();
	private final ConcurrentLinkedQueue<LocalImpl> locals = new ConcurrentLinkedQueue<LocalImpl>();

	/**
	 * @return returns an Argument scope
	 * most arguments have only 0-3 keys
	 */
	public Argument getArgumentInstance(Object[] args) {
		if (args == null || args.length < ArgumentImpl.ARG_DEFAULT_INITIAL_CAPACITY){
			Argument arg = arguments.poll();
			if (arg != null) {
				return arg;
			}
			return new ArgumentImpl();
		}
		Argument arg = argumentsLarge.poll();
		if (arg != null) {
			return arg;
		}
		return new ArgumentImpl(ArgumentImpl.ARG_DEFAULT_INITIAL_CAPACITY * 2);
	}

	/**
	 * @return retruns a Local Instance
	 */
	public LocalImpl getLocalInstance() {
		LocalImpl lcl = locals.poll();
		if (lcl != null) {
			return lcl;
		}
		//aprint.o("local new! " + locals.size());
		return new LocalImpl();
	}

	/**
	 * @param argument recycle an Argument scope for reuse
	 */
	public void recycle(PageContext pc, Argument argument) {
		if (argument.isBind()) return;
		if (argument.size() < ArgumentImpl.ARG_DEFAULT_INITIAL_CAPACITY ){
			if (arguments.size() >= MAX_SIZE ) return;
			argument.release(pc);
			arguments.add(argument);
		} else {
			if (argumentsLarge.size() >= MAX_SIZE ) return;
			argument.release(pc);
			argumentsLarge.add(argument);
		}
	}

	/**
	 * @param local recycle a Local scope for reuse
	 */
	public void recycle(PageContext pc, LocalImpl local) {
		if (locals.size() >= MAX_SIZE || local.isBind()) return;
		//if (local.size() > 0) aprint.o("local: " + local.size());
		local.release(pc);
		locals.add(local);
	}

	/**
	 * cast an int scope definition to a string definition
	 * 
	 * @param scope
	 * @return
	 */
	public static String toStringScope(int scope, String defaultValue) {
		switch (scope) {
		case Scope.SCOPE_APPLICATION:
			return "application";
		case Scope.SCOPE_ARGUMENTS:
			return "arguments";
		case Scope.SCOPE_CALLER:
			return "caller";
		case Scope.SCOPE_CGI:
			return "cgi";
		case Scope.SCOPE_CLIENT:
			return "client";
		case Scope.SCOPE_COOKIE:
			return "cookie";
		case Scope.SCOPE_FORM:
			return "form";
		case Scope.SCOPE_VAR:
		case Scope.SCOPE_LOCAL:
			return "local";
		case Scope.SCOPE_REQUEST:
			return "request";
		case Scope.SCOPE_SERVER:
			return "server";
		case Scope.SCOPE_SESSION:
			return "session";
		case Scope.SCOPE_UNDEFINED:
			return "undefined";
		case Scope.SCOPE_URL:
			return "url";
		case Scope.SCOPE_VARIABLES:
			return "variables";
		case Scope.SCOPE_CLUSTER:
			return "thread";
		}

		return defaultValue;
	}

}