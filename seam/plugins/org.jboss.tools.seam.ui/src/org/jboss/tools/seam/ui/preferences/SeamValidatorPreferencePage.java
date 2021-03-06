/*******************************************************************************
 * Copyright (c) 2007 Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Red Hat, Inc. - initial API and implementation
 ******************************************************************************/ 

package org.jboss.tools.seam.ui.preferences;

import org.eclipse.swt.widgets.Composite;
import org.eclipse.ui.preferences.IWorkbenchPreferenceContainer;
import org.jboss.tools.common.ui.preferences.SeverityPreferencePage;
import org.jboss.tools.common.ui.preferences.SeverityConfigurationBlock.SectionDescription;
import org.jboss.tools.seam.core.SeamCorePlugin;
import org.jboss.tools.seam.internal.core.validation.SeamValidationErrorManager;

/**
 * @author Viacheslav Kabanovich
 */
public class SeamValidatorPreferencePage extends SeverityPreferencePage {
	public static final String PREF_ID = SeamValidationErrorManager.PREFERENCE_PAGE_ID; 
	public static final String PROP_ID = "org.jboss.tools.seam.ui.propertyPages.SeamValidatorPreferencePage"; //$NON-NLS-1$

	public SeamValidatorPreferencePage() {
		setPreferenceStore(SeamCorePlugin.getDefault().getPreferenceStore());
		setTitle(SeamPreferencesMessages.SEAM_VALIDATOR_PREFERENCE_PAGE_SEAM_VALIDATOR);
	}

	@Override
	protected String getPreferencePageID() {
		return PREF_ID;
	}

	@Override
	protected String getPropertyPageID() {
		return PROP_ID;
	}

	@Override
	public void createControl(Composite parent) {
		IWorkbenchPreferenceContainer container = (IWorkbenchPreferenceContainer) getContainer();
		fConfigurationBlock = new SeamValidatorConfigurationBlock(getNewStatusChangedListener(), getProject(), container);

		super.createControl(parent);
	}
	
	@Override
	protected SectionDescription[] getAllSections() {
		return SeamValidatorConfigurationBlock.ALL_SECTIONS;
	}
}