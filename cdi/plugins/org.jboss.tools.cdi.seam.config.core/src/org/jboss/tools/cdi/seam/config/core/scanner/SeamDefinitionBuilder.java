/******************************************************************************* 
 * Copyright (c) 2011 Red Hat, Inc. 
 * Distributed under license by Red Hat, Inc. All rights reserved. 
 * This program is made available under the terms of the 
 * Eclipse Public License v1.0 which accompanies this distribution, 
 * and is available at http://www.eclipse.org/legal/epl-v10.html 
 * 
 * Contributors: 
 * Red Hat, Inc. - initial API and implementation 
 ******************************************************************************/
package org.jboss.tools.cdi.seam.config.core.scanner;

import java.io.ByteArrayInputStream;
import java.util.List;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.jdt.core.IField;
import org.eclipse.jdt.core.IMember;
import org.eclipse.jdt.core.IMemberValuePair;
import org.eclipse.jdt.core.IMethod;
import org.eclipse.jdt.core.IType;
import org.eclipse.jdt.core.JavaModelException;
import org.eclipse.jface.text.IDocument;
import org.eclipse.osgi.util.NLS;
import org.jboss.tools.cdi.core.CDIConstants;
import org.jboss.tools.cdi.core.CDICoreNature;
import org.jboss.tools.cdi.internal.core.impl.definition.AnnotationDefinition;
import org.jboss.tools.cdi.seam.config.core.CDISeamConfigConstants;
import org.jboss.tools.cdi.seam.config.core.CDISeamConfigCorePlugin;
import org.jboss.tools.cdi.seam.config.core.ConfigDefinitionContext;
import org.jboss.tools.cdi.seam.config.core.definition.AbstractSeamFieldDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamBeanDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamBeansDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamFieldDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamFieldValueDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamMethodDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamParameterDefinition;
import org.jboss.tools.cdi.seam.config.core.definition.SeamVirtualFieldDefinition;
import org.jboss.tools.cdi.seam.config.core.util.Util;
import org.jboss.tools.cdi.seam.config.core.validation.SeamConfigValidationMessages;
import org.jboss.tools.cdi.seam.config.core.xml.SAXAttribute;
import org.jboss.tools.cdi.seam.config.core.xml.SAXElement;
import org.jboss.tools.cdi.seam.config.core.xml.SAXParser;
import org.jboss.tools.cdi.seam.config.core.xml.SAXText;
import org.jboss.tools.common.java.IJavaAnnotation;
import org.jboss.tools.common.java.impl.AnnotationLiteral;

/**
 * 
 * @author Viacheslav Kabanovich
 *
 */
public class SeamDefinitionBuilder {
	static int IN_ANNOTATION_TYPE = 1;

	CDICoreNature project;
	ConfigDefinitionContext context;
	IResource resource;
	SeamBeansDefinition result;
	SAXElement root;

	public SeamBeansDefinition createDefinition(IResource resource, IDocument document, CDICoreNature project, ConfigDefinitionContext context) {
		this.project = project;
		this.context = context;
		this.resource = resource;
		
		result = new SeamBeansDefinition();
		result.setResource(resource);
		if(document.get().indexOf("<") >= 0) { // file can be empty
			SAXParser parser = new SAXParser();
			String text = document.get();
			ByteArrayInputStream s = new ByteArrayInputStream(text.getBytes());
			root = parser.parse(s, document);
			scanRoot();
		}

		return result;
	}

	private void scanRoot() {
		if(root == null) return;
		List<SAXElement> es = root.getChildElements();
		for (SAXElement element: es) {
			scanElement(element);
		}
	}

	private void scanElement(SAXElement element) {
		if(!Util.isConfigRelevant(element)) return;

		IType type = Util.resolveType(element, project);
		if(type == null) {
			reportUnresolvedType(element);
			result.addPossibleTypeNames(Util.getPossibleTypeNames(element));
			return;
		}
		TypeCheck typeCheck = new TypeCheck(type, element);
		if(typeCheck.isCorrupted) return;
		if(typeCheck.isAnnotation) {
			scanAnnotation(element, type);
		} else if(Util.hasProducesChild(element)) {
			SeamVirtualFieldDefinition f = scanVirtualProducerField(element);
			if(f != null) {
				result.addVirtualField(f);
			}
			
		} else {
			scanBean(element, type, false);
		}
	}

	private void reportUnresolvedType(SAXElement element) {
		Set<String> ps = Util.getPossibleTypeNames(element);
		if(ps.size() == 1) {
			String type = ps.iterator().next();
			String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_TYPE, type);
			result.addUnresolvedNode(element, CDISeamConfigConstants.ERROR_UNRESOLVED_TYPE, message);
		} else {
			String type = element.getLocalName();
			String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_TYPE, type);
			result.addUnresolvedNode(element, CDISeamConfigConstants.ERROR_UNRESOLVED_TYPE, message);
		}
	}

	private void scanAnnotation(SAXElement element, IType type) {
		context.getRootContext().getAnnotationKind(type); // kick it
		AnnotationDefinition def = new AnnotationDefinition();
		def.setType(type, context.getRootContext(), 0);

		List<SAXElement> es = element.getChildElements();
		//children should be annotation declarations.
		for (SAXElement c: es) {
			IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
			if(a != null) def.addAnnotation(a, context.getRootContext());
		}
		
		def.revalidateKind(context.getRootContext());
	
		context.addAnnotation(type.getFullyQualifiedName(), def);

	}

	private SeamBeanDefinition scanBean(SAXElement element, IType type, boolean inline) {
		addDependency(type);
		SeamBeanDefinition def = new SeamBeanDefinition();
		def.setResource(resource);
		def.setInline(inline);
		def.setNode(element);
		def.setType(type);
		result.addBeanDefinition(def);
		List<SAXElement> es = element.getChildElements();
		for (SAXElement c: es) {
			if(!Util.isConfigRelevant(c)) continue;
			if(Util.containsEEPackage(c)) {
				if(CDISeamConfigConstants.KEYWORD_REPLACES.equals(c.getLocalName())) {
					def.setReplaces(c);
					continue;
				}
				if(CDISeamConfigConstants.KEYWORD_MODIFIES.equals(c.getLocalName())) {
					def.setModifies(c);
					continue;
				}
				if(Util.isParameters(c)) {
					SeamMethodDefinition md = scanConstructor(c, type);
					if(md != null) def.addMethod(md);
					continue;
				}
			}
			IType t = Util.resolveType(c, project);
			if(t != null) {
				IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
				if(a != null) def.addAnnotation(a);
				continue;
			}
			IMember m = null;
			if(c.getURI() != null && c.getURI().equals(element.getURI())) try {
				m = Util.resolveMember(type, c);
			} catch (JavaModelException e) {
				CDISeamConfigCorePlugin.getDefault().logError(e);
			}
			if(m instanceof IField) {
				def.addField(scanField(c, (IField)m));
			} else if(m instanceof IMethod) {
				SeamMethodDefinition md = scanMethod(c, type);
				if(md != null) def.addMethod(md);
			} else {
				reportUnresolvedMember(c, type);
			}
		}
		Set<String> as = element.getAttributeNames();
		for (String name: as) {
			SAXAttribute a = element.getAttribute(name);
			IField f = type.getField(name);
			if(f == null || !f.exists()) {
				reportUnresolvedField(a, type);
			} else {
				def.addField(scanField(a, f));
			}
		}
		return def;
	}

	void reportUnresolvedMember(SAXElement c, IType type) {
		String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_MEMBER, c.getLocalName(), type.getElementName());
		result.addUnresolvedNode(c, CDISeamConfigConstants.ERROR_UNRESOLVED_MEMBER, message);
	}

	void reportUnresolvedMethod(SAXElement c, IType type, String params) {
		String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_METHOD, c.getLocalName() + "(" + params + ")", type.getElementName());
		result.addUnresolvedNode(c, CDISeamConfigConstants.ERROR_UNRESOLVED_METHOD, message);
	}

	void reportUnresolvedConstructor(SAXElement c, IType type, String params) {
		String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_CONSTRUCTOR, type.getElementName() + "(" + params + ")");
		result.addUnresolvedNode(c, CDISeamConfigConstants.ERROR_UNRESOLVED_CONSTRUCTOR, message);
	}

	void reportUnresolvedField(SAXAttribute c, IType type) {
		String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_FIELD, c.getName(), type.getElementName());
		result.addUnresolvedNode(c, CDISeamConfigConstants.ERROR_UNRESOLVED_MEMBER, message);
	}

	void reportUnresolvedMethod(SAXAttribute c, IType type) {
		String message = NLS.bind(SeamConfigValidationMessages.UNRESOLVED_METHOD, c.getName() + "()", type.getElementName());
		result.addUnresolvedNode(c, CDISeamConfigConstants.ERROR_UNRESOLVED_METHOD, message);
	}

	private SeamVirtualFieldDefinition scanVirtualProducerField(SAXElement element) {
		SeamVirtualFieldDefinition def = new SeamVirtualFieldDefinition();
		def.setResource(resource);
		def.setNode(element);
		IType type = Util.resolveType(element, project);
		if(type == null) {
			reportUnresolvedType(element);
			return null;
		}
		def.setType(type);
		scanFieldContent(def, element);
		return def;
	}

	private SeamFieldDefinition scanField(SAXElement element, IField field) {
		SeamFieldDefinition def = new SeamFieldDefinition();
		def.setResource(resource);
		def.setNode(element);
		def.setField(field);
		scanFieldContent(def, element);
		return def;
	}

	private void scanFieldContent(AbstractSeamFieldDefinition def, SAXElement element) {
		if(Util.hasText(element)) {
			def.addValue(element.getTextNode());
		}
		List<SAXElement> es = element.getChildElements();
		for (SAXElement c: es) {
			if(!Util.isConfigRelevant(c)) continue;
			if(Util.isValue(c)) {
				if(Util.hasText(c)) {
					def.addValue(c.getTextNode());
				} else {
					scanFieldValue(def, c);
				}
				continue;
			} else if(Util.isEntry(c)) {
				scanEntry(def, c);
				continue;
			}
			IType t = Util.resolveType(c, project);
			if(t != null) {
				IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
				if(a != null) def.addAnnotation(a);
				continue;
			} else {
				reportUnresolvedType(c);
			}
		
		}		
	}

	private SeamFieldDefinition scanField(SAXAttribute a, IField field) {
		SeamFieldDefinition def = new SeamFieldDefinition();
		def.setResource(resource);
		def.setNode(a);
		def.setField(field);
		def.addValue(a);
		return def;
	}	

	/**
	 * Scan field value for inline bean declarations. 
	 * @param element
	 */
	private void scanFieldValue(AbstractSeamFieldDefinition def, SAXElement element) {
		if(!Util.isConfigRelevant(element)) return;
		List<SAXElement> es = element.getChildElements();
		for (SAXElement c: es) {
			if(!Util.isConfigRelevant(c)) continue;
			IType type = Util.resolveType(c, project);
			if(type == null) continue;
			TypeCheck typeCheck = new TypeCheck(type, c);
			if(typeCheck.isCorrupted) return;
			if(!typeCheck.isAnnotation) {
				SeamBeanDefinition inline = scanBean(c, type, true);
				IJavaAnnotation q = createInlineBeanQualifier();
				if(q != null) {
					SeamFieldValueDefinition vdef = new SeamFieldValueDefinition();
					vdef.setResource(resource);
					vdef.setNode(element);
					inline.addAnnotation(q);
					vdef.addAnnotation(q);
					vdef.setInlineBean(inline);
					IJavaAnnotation inject = createInject(element);
					if(inject != null) {
						vdef.addAnnotation(inject);
					}
					def.addValueDefinition(vdef);
				}
			}
		}
	}

	private void scanEntry(AbstractSeamFieldDefinition def, SAXElement element) {
		List<SAXElement> es = element.getChildElements();
		SAXText key = null;
		SAXText value = null;
		for (SAXElement c: es) {
			if(!Util.isConfigRelevant(c)) continue;
			if(Util.isKey(c)) {
				if(Util.hasText(c)) {
					key = c.getTextNode();
				} else {
					scanFieldValue(def, c);
				}
			}
			if(Util.isValue(c)) {
				if(Util.hasText(c)) {
					value = c.getTextNode();
				} else {
					scanFieldValue(def, c);
				}
			}
		}
		if(key != null && value != null) {
			def.addValue(key, value);
		}
	}

	private SeamMethodDefinition scanMethod(SAXElement element, IType type) {
		SeamMethodDefinition def = new SeamMethodDefinition();
		def.setResource(resource);
		def.setNode(element);
		StringBuilder paramPresentation = new StringBuilder();
		List<SAXElement> es = element.getChildElements();
		for (SAXElement c: es) {
			if(!Util.isConfigRelevant(c)) continue;
			if(Util.isParameters(c)) {
				List<SAXElement> ps = c.getChildElements();
				for (SAXElement p: ps) {
					SeamParameterDefinition pd = scanParameter(p, paramPresentation);
					if(pd != null) def.addParameter(pd);
				}
				continue;
			} else if(Util.isArray(c)) {
				SeamParameterDefinition pd = scanParameter(c, paramPresentation);
				if(pd != null) def.addParameter(pd);
				continue;
			}
			IType t = Util.resolveType(c, project);
			if(t != null) {
				IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
				if(a != null) def.addAnnotation(a);
				continue;
			} else {
				reportUnresolvedType(c);
			}
		
		}		
		IMethod method = null;
		try {
			method = Util.findMethod(def, type, element.getLocalName(), context.getRootContext());
		} catch (JavaModelException e) {
			CDISeamConfigCorePlugin.getDefault().logError(e);
		}
		if(method != null) {
			def.setMethod(method);
		} else {
			reportUnresolvedMethod(element, type, paramPresentation.toString());
			def = null;
		}
		return def;
	}

	private SeamMethodDefinition scanConstructor(SAXElement element, IType type) {
		SeamMethodDefinition def = new SeamMethodDefinition();
		def.setResource(resource);
		def.setNode(element);
		StringBuilder paramPresentation = new StringBuilder();
		if(Util.isParameters(element)) {
			List<SAXElement> ps = element.getChildElements();
			for (SAXElement p: ps) {
				SeamParameterDefinition pd = scanParameter(p, paramPresentation);
				if(pd != null) def.addParameter(pd);
			}
		} else if(Util.isArray(element)) {
			SeamParameterDefinition pd = scanParameter(element, paramPresentation);
			if(pd != null) def.addParameter(pd);
		}
		IJavaAnnotation inject = createInject(element);
		if(inject != null) def.addAnnotation(inject);
		IMethod method = null;
		try {
			method = Util.findMethod(def, type, null, context.getRootContext());
		} catch (JavaModelException e) {
			CDISeamConfigCorePlugin.getDefault().logError(e);
		}
		if(method != null) {
			def.setMethod(method);
		} else {
			reportUnresolvedConstructor(element, type, paramPresentation.toString());
			def = null;
		}
		
		return def;
	}

	private SeamParameterDefinition scanParameter(SAXElement element, StringBuilder paramPresentation) {
		if(!Util.isConfigRelevant(element)) return null;
		SeamParameterDefinition def = new SeamParameterDefinition();
		def.setResource(resource);
		def.setNode(element);
		if(Util.isArray(element)) {
			if(element.hasAttribute(CDISeamConfigConstants.ATTR_DIMENSIONS)) {
				def.setDimensions(element.getAttribute(CDISeamConfigConstants.ATTR_DIMENSIONS).getValue());
			} else {
				def.setDimensions("1");
			}
			List<SAXElement> es = element.getChildElements();
			for (SAXElement c: es) {
				if(!Util.isConfigRelevant(c)) continue;

				if(paramPresentation.length() > 0) paramPresentation.append(",");
				paramPresentation.append(c.getLocalName());
				for (int q = 0; q < def.getDimensions(); q++) paramPresentation.append("[]");

				IType type = Util.resolveType(c, project);
				if(type == null) {
					reportUnresolvedType(c);
					continue;
				}
				TypeCheck typeCheck = new TypeCheck(type, c);
				if(typeCheck.isCorrupted) continue;
				if(typeCheck.isAnnotation) {
					IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
					if(a != null) def.addAnnotation(a);
				} else {
					def.setType(type);
				}
			}
		} else {
			if(paramPresentation.length() > 0) paramPresentation.append(",");
			paramPresentation.append(element.getLocalName());

			IType type = Util.resolveType(element, project);
			if(type == null) {
				reportUnresolvedType(element);
				return null;
			}
			def.setType(type);
			List<SAXElement> es = element.getChildElements();
			for (SAXElement c: es) {
				if(!Util.isConfigRelevant(c)) {
					continue; //report?
				}
				if(Util.containsEEPackage(c)) continue; //we are not interested yet
				IType t = Util.resolveType(c, project);
				if(t != null) {
					IJavaAnnotation a = loadAnnotationDeclaration(c, IN_ANNOTATION_TYPE);
					if(a != null) def.addAnnotation(a);
					continue;
				}
			}
		}

		return def;
	}

	private IJavaAnnotation loadAnnotationDeclaration(SAXElement element, int contextKind) {
		if(!Util.isConfigRelevant(element)) return null;
		
		IType type = Util.resolveType(element, project);
		if(type == null) {
			if(contextKind == IN_ANNOTATION_TYPE) {
				reportUnresolvedType(element);
			}
			return null;
		}
		TypeCheck typeCheck = new TypeCheck(type, element);
		if(typeCheck.isCorrupted) return null;
		if(typeCheck.isAnnotation) {
			addDependency(type);
			context.getRootContext().getAnnotationKind(type); // kick it
			String value = null;
			SAXText text = element.getTextNode();
			if(text != null && text.getValue() != null && text.getValue().trim().length() > 0) {
				value = text.getValue();
			}
			AnnotationLiteral literal = new AnnotationLiteral(resource,  
					element.getLocation().getStartPosition(), element.getLocation().getLength(), 
					value, IMemberValuePair.K_STRING, type);
			Set<String> ns = element.getAttributeNames();
			for (String n: ns) {
				SAXAttribute attr = element.getAttribute(n);
				String v = attr.getValue();
				literal.addMemberValuePair(n, v, IMemberValuePair.K_STRING);
				IMethod m = type.getMethod(n, new String[0]);
				if(!m.exists()) {
					reportUnresolvedMethod(attr, type);
				}
			}
			return literal;
		} else if(contextKind == IN_ANNOTATION_TYPE) {
			result.addUnresolvedNode(element, CDISeamConfigConstants.ERROR_ANNOTATION_EXPECTED, SeamConfigValidationMessages.ANNOTATION_EXPECTED);
		}		
		return null;
	}

	class TypeCheck {
		boolean isCorrupted = false;
		boolean isAnnotation = false;
		TypeCheck(IType type, SAXElement element) {
			try {
				isAnnotation = type.isAnnotation();
			} catch (JavaModelException e) {
				CDISeamConfigCorePlugin.getDefault().logError(e);
				reportUnresolvedType(element);
				isCorrupted = true;
			}
		}
	}

	static long inlineBeanCount = 0;
	
	IJavaAnnotation createInlineBeanQualifier() {
		IType type = project.getType(CDISeamConfigConstants.INLINE_BEAN_QUALIFIER);
		if(type == null) {
			type = project.getType(CDISeamConfigConstants.INLINE_BEAN_QUALIFIER_30);
		}
		if(type == null) {
			return null;
		}
		long id = inlineBeanCount++;
		return new AnnotationLiteral(resource, 0, 0, "" + id, IMemberValuePair.K_STRING, type);
	}

	IJavaAnnotation createInject(SAXElement forElement) {
		IType type = project.getType(CDIConstants.INJECT_ANNOTATION_TYPE_NAME);
		return (type == null) ? null : new AnnotationLiteral(resource, forElement.getLocation().getStartPosition(), forElement.getLocation().getLength(), null, 0, type);
	}

	private void addDependency(IType type) {
		if(!type.exists() || type.isBinary()) return;
		if(!resource.exists() || resource.getName().endsWith(".jar")) return;
		//beans.xml depends on type
		context.getRootContext().addDependency(type.getResource().getFullPath(), resource.getFullPath());
		//though type does not depend on beans.xml it has to be revalidated. Maybe it should be method addValidationDependency.
		context.getRootContext().addDependency(resource.getFullPath(), type.getResource().getFullPath());
	}

}
