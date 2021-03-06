/*******************************************************************************
 * Copyright (c) 2007 Exadel, Inc. and Red Hat, Inc.
 * Distributed under license by Red Hat, Inc. All rights reserved.
 * This program is made available under the terms of the
 * Eclipse Public License v1.0 which accompanies this distribution,
 * and is available at http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     Exadel, Inc. and Red Hat, Inc. - initial API and implementation
 ******************************************************************************/ 
package org.jboss.tools.struts.model.handlers.page;

import java.util.*;
import org.jboss.tools.common.model.*;
import org.jboss.tools.common.meta.action.*;
import org.jboss.tools.common.meta.action.impl.*;
import org.jboss.tools.struts.*;

public class HiddenLinksHandler extends AbstractHandler implements StrutsConstants {
//    SpecialWizard wizard = SpecialWizardFactory.createSpecialWizard("org.jboss.tools.jst.web.ui.wizards.links.HiddenLinksWizard");

    public HiddenLinksHandler() {}

    public boolean isEnabled(XModelObject object) {
        if(/*wizard != null &&*/ object == null || !object.isObjectEditable()) return false;
        if(!ENT_PROCESSITEM.equals(object.getModelEntity().getName())) return false;
        return (TYPE_PAGE.equals(object.getAttributeValue(ATT_TYPE)));
    }

    //! 'short' is equivalent to 'no'

    public void executeHandler(XModelObject object, Properties p) throws XModelException {
        if(!isEnabled(object)) return;
		SpecialWizard wizard = SpecialWizardFactory.createSpecialWizard("org.jboss.tools.jst.web.ui.wizards.links.HiddenLinksWizard");
        XModelObject[] links = object.getChildren();
        String[][] vs = new String[links.length][];
        for (int i = 0; i < vs.length; i++) {
          vs[i] = new String[]{links[i].getAttributeValue("path"), links[i].getAttributeValue("hidden")};
        }
        if(p == null) p = new Properties();
        p.put("data", vs);
        p.put("model", object.getModel());
        p.setProperty("help", "StrutsProcessItem_ShowHideLinks");
        wizard.setObject(p);
        if(wizard.execute() != 0) return;
        for (int i = 0; i < vs.length; i++) {
          if("yes".equals(links[i].getAttributeValue("hidden")) == "yes".equals(vs[i][1])) continue; //!
          links[i].getModel().changeObjectAttribute(links[i], "hidden", vs[i][1]);
        }
    }

}
