/////////////////////////////////////////////////////////////////////////////
//
// Project ProjectForge Community Edition
//         www.projectforge.org
//
// Copyright (C) 2001-2012 Kai Reinhard (k.reinhard@micromata.com)
//
// ProjectForge is dual-licensed.
//
// This community edition is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License as published
// by the Free Software Foundation; version 3 of the License.
//
// This community edition is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU General
// Public License for more details.
//
// You should have received a copy of the GNU General Public License along
// with this program; if not, see http://www.gnu.org/licenses/.
//
/////////////////////////////////////////////////////////////////////////////

package org.projectforge.web;

import java.util.Map;

import org.apache.wicket.Session;
import org.apache.wicket.markup.html.WebPage;
import org.junit.Test;
import org.projectforge.core.SystemInfoCache;
import org.projectforge.registry.DaoRegistry;
import org.projectforge.registry.Registry;
import org.projectforge.test.TestBase;
import org.projectforge.test.TestConfiguration;
import org.projectforge.web.address.AddressMobileViewPage;
import org.projectforge.web.address.AddressViewPage;
import org.projectforge.web.admin.SetupPage;
import org.projectforge.web.doc.TutorialPage;
import org.projectforge.web.mobile.LoginMobilePage;
import org.projectforge.web.registry.WebRegistry;
import org.projectforge.web.scripting.ScriptExecutePage;
import org.projectforge.web.wicket.MessagePage;
import org.projectforge.web.wicket.MySession;
import org.projectforge.web.wicket.WicketPageTestBase;

public class CallAllPagesTest extends WicketPageTestBase
{
  private static final org.apache.log4j.Logger log = org.apache.log4j.Logger.getLogger(CallAllPagesTest.class);

  int counter;

  @SuppressWarnings("unchecked")
  private final Class< ? extends WebPage>[] skipPages = new Class[] { //
    // Checked below:
    LoginPage.class, LoginMobilePage.class, SetupPage.class, TutorialPage.class, //
    AddressViewPage.class, // Checked in AddressPagesTest
    // Not yet checked:
    AddressMobileViewPage.class, // Needs id of an address.
    ScriptExecutePage.class};

  @Test
  public void testAllMountedPages()
  {
    testAllMountedPages(BrowserScreenWidthType.NARROW);
    testAllMountedPages(BrowserScreenWidthType.NORMAL);
    testAllMountedPages(BrowserScreenWidthType.WIDE);
    testPage(LoginPage.class);
    testPage(LoginMobilePage.class);
    clearDatabase();
    deleteDB();
    Registry.instance().getUserGroupCache().setExpired();
    testPage(SetupPage.class);
    log.info("Number of tested Wicket pages: " + counter);
  }

  private void testAllMountedPages(final BrowserScreenWidthType browserScreenWidthType)
  {
    log.info("Test all web pages with resolution '" + browserScreenWidthType + "'.");
    login(TestBase.TEST_FULL_ACCESS_USER, TestBase.TEST_FULL_ACCESS_USER_PASSWORD);
    ((MySession) Session.get()).setBrowserScreenWidthType(browserScreenWidthType);
    final DaoRegistry daoRegistry = TestConfiguration.getConfiguration().getBean("daoRegistry", DaoRegistry.class);
    daoRegistry.init();
    final SystemInfoCache systemInfoCache = TestConfiguration.getConfiguration().getBean("systemInfoCache", SystemInfoCache.class);
    SystemInfoCache.internalInitialize(systemInfoCache);
    final Map<String, Class< ? extends WebPage>> pages = WebRegistry.instance().getMountPages();
    counter = 0;
    for (final Map.Entry<String, Class< ? extends WebPage>> entry : pages.entrySet()) {
      boolean skip = false;
      for (final Class< ? > clazz : skipPages) {
        if (clazz.equals(entry.getValue()) == true) {
          log.info("Skipping page: " + entry.getValue());
          skip = true;
        }
      }
      if (skip == true) {
        continue;
      }
      testPage(entry.getValue());
    }
    testPage(TutorialPage.class, MessagePage.class); // Tutorial page not available at default.
    logout();
  }

  private void testPage(final Class< ? extends WebPage> pageClass)
  {
    testPage(pageClass, pageClass);
  }

  private void testPage(final Class< ? extends WebPage> pageClass, final Class< ? extends WebPage> expectedRenderedPage)
  {
    log.info("Calling page: " + pageClass.getName());
    tester.startPage(pageClass);
    tester.assertRenderedPage(expectedRenderedPage);
    ++counter;
  }
}
