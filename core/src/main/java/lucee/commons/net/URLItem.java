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
package lucee.commons.net;

/**
 * Name Value Pair
 */
public final class URLItem {

	private String name;
	private String value;
	// The name and the value each carry their own decode flag: a query item can
	// have a decodable name and a raw value (or vice-versa), e.g. "a%20b=zac+%".
	private boolean nameUrlEncoded;
	private boolean valueUrlEncoded;

	/**
	 * @param name
	 * @param value
	 * @param urlEncoded applies to both name and value
	 */
	public URLItem(String name, String value, boolean urlEncoded) {
		this(name, value, urlEncoded, urlEncoded);
	}

	/**
	 * @param name
	 * @param value
	 * @param nameUrlEncoded the name needs decoding
	 * @param valueUrlEncoded the value needs decoding
	 */
	public URLItem(String name, String value, boolean nameUrlEncoded, boolean valueUrlEncoded) {
		this.name = name;
		this.value = value;
		this.nameUrlEncoded = nameUrlEncoded;
		this.valueUrlEncoded = valueUrlEncoded;
	}

	/**
	 * @return whether the name or the value needs decoding
	 */
	public boolean isUrlEncoded() {
		return nameUrlEncoded || valueUrlEncoded;
	}

	/**
	 * @return whether the name needs decoding
	 */
	public boolean isNameUrlEncoded() {
		return nameUrlEncoded;
	}

	/**
	 * @return whether the value needs decoding
	 */
	public boolean isValueUrlEncoded() {
		return valueUrlEncoded;
	}

	/**
	 * @param urlEncoded the urlEncoded to set for both name and value
	 */
	public void setUrlEncoded(boolean urlEncoded) {
		this.nameUrlEncoded = urlEncoded;
		this.valueUrlEncoded = urlEncoded;
	}

	/**
	 * @return Returns the name.
	 */
	public String getName() {
		return name;
	}

	/**
	 * @param name The name to set.
	 */
	public void setName(String name) {
		this.name = name;
	}

	/**
	 * @return Returns the value.
	 */
	public String getValue() {
		return value;
	}

	/**
	 * @param value The value to set.
	 */
	public void setValue(String value) {
		this.value = value;
	}

}