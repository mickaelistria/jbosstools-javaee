/*******************************************************************************
 * Copyright (c) 2007-2011 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/
package org.jboss.tools.jsf.web.validation.i18n;

import org.eclipse.wst.xml.core.internal.provisional.document.IDOMAttr;
import org.eclipse.wst.xml.core.internal.provisional.document.IDOMText;
import org.jboss.tools.jsf.messages.JSFUIMessages;
import org.jboss.tools.jsf.web.validation.JSFAbstractValidationComponent;
import org.jboss.tools.jst.jsp.JspEditorPlugin;

/**
 * @author mareshkau
 *
 */
public class I18nValidationComponent extends JSFAbstractValidationComponent{
	public static String PROBLEM_ID = JspEditorPlugin.I18N_VALIDATION_PROBLEM_ID;
	
	private String inValidString;

	//component creating usung factory method
	private I18nValidationComponent(){}
	
	public static I18nValidationComponent createI18nValidationComponent(IDOMText element){
		I18nValidationComponent component =  new I18nValidationComponent();
		component.setStartOffSet(element.getStartOffset());
		component.setLength(element.getLength());
		if(element.getStructuredDocument()==null) {
			return null;
		}
		component.setLine(element.getStructuredDocument().getLineOfOffset(
				component.getStartOffSet()) + 1);
		component.createValidationMessage();
		component.createMessageParams();
		component.setInValidString(element.getNodeValue());
		return component;
	}
	
	public static I18nValidationComponent createI18nValidationComponent(IDOMAttr attr){
		I18nValidationComponent component =  new I18nValidationComponent();
		component.setStartOffSet(attr.getValueRegionStartOffset()+1);
		component.setLength(attr.getValueRegionText().length()-2);
		if(attr.getStructuredDocument()==null) {
			return null;
		}
		component.setLine(attr.getStructuredDocument().getLineOfOffset(
				component.getStartOffSet()) + 1);
		component.createValidationMessage();
		component.createMessageParams();
		component.setInValidString(attr.getNodeValue());
		return component;
	}
	


	public String getType() {
		return null;
	}


	public String getComponentResourceLocation() {
		return null;
	}


	public void createValidationMessage() {
		setValidationMessage(JSFUIMessages.NonExternalizedStringLiteral);
	}

	/**
	 * @param inValidString the inValidString to set
	 */
	public void setInValidString(String inValidString) {
		this.inValidString = inValidString;
	}

	/**
	 * @return the inValidString
	 */
	public String getInValidString() {
		return inValidString;
	}

}
