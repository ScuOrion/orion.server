/*******************************************************************************
 * Copyright (c) 2013 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.orion.server.cf.objects;

import java.net.*;
import org.eclipse.orion.server.cf.CFProtocolConstants;
import org.eclipse.orion.server.core.resources.Property;
import org.eclipse.orion.server.core.resources.ResourceShape;
import org.eclipse.orion.server.core.resources.annotations.PropertyDescription;
import org.eclipse.orion.server.core.resources.annotations.ResourceDescription;
import org.json.JSONException;
import org.json.JSONObject;

@ResourceDescription(type = Target.TYPE)
public class Target extends CFObject {

	public static final String RESOURCE = "target"; //$NON-NLS-1$
	public static final String TYPE = "Target"; //$NON-NLS-1$

	private static final ResourceShape DEFAULT_RESOURCE_SHAPE = new ResourceShape();
	{
		Property[] defaultProperties = new Property[] { //
		new Property(CFProtocolConstants.KEY_URL) //
		};
		DEFAULT_RESOURCE_SHAPE.setProperties(defaultProperties);
	}

	//	private String name;

	private URL url;

	//	private String user;

	private JSONObject accessToken;

	private JSONObject org;

	private JSONObject space;

	public Target() {
	}

	public Target(Target target) {
		this.setUrl(target.getUrl());
		this.setAccessToken(target.getAccessToken());
		this.setOrg(target.getOrg());
		this.setSpace(target.getSpace());
	}

	@Override
	protected URI getLocation() throws URISyntaxException {
		return null;
	}

	//	public String getName() {
	//		return name;
	//	}
	//
	//	public void setName(String name) {
	//		this.name = name;
	//	}

	//	public String getUser() {
	//		return user;
	//	}
	//
	//	public void setUser(String user) {
	//		this.user = user;
	//	}

	@PropertyDescription(name = CFProtocolConstants.KEY_URL)
	public URL getUrl() {
		return url;
	}

	public void setUrl(URL url) {
		this.url = url;
	}

	public JSONObject getAccessToken() {
		return accessToken;
	}

	public void setAccessToken(JSONObject accessToken) {
		this.accessToken = accessToken;
	}

	public JSONObject getOrg() {
		return org;
	}

	public void setOrg(JSONObject org) {
		this.org = org;
	}

	public JSONObject getSpace() {
		return space;
	}

	public void setSpace(JSONObject space) {
		this.space = space;
	}

	@Override
	public JSONObject toJSON() throws JSONException {
		return jsonSerializer.serialize(this, DEFAULT_RESOURCE_SHAPE);
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((url == null) ? 0 : url.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		Target other = (Target) obj;
		if (url == null) {
			if (other.url != null)
				return false;
		} else if (!url.equals(other.url))
			return false;
		return true;
	}
}
