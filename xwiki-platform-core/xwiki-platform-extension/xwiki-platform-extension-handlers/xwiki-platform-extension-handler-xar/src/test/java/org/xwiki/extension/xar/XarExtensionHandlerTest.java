/*
 * See the NOTICE file distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */
package org.xwiki.extension.xar;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import junit.framework.Assert;

import org.jmock.Expectations;
import org.jmock.lib.action.CustomAction;
import org.junit.Before;
import org.junit.Test;
import org.xwiki.extension.ExtensionId;
import org.xwiki.extension.job.InstallRequest;
import org.xwiki.extension.job.UninstallRequest;
import org.xwiki.extension.job.internal.InstallJob;
import org.xwiki.extension.job.internal.UninstallJob;
import org.xwiki.extension.repository.InstalledExtensionRepository;
import org.xwiki.extension.test.RepositoryUtil;
import org.xwiki.extension.xar.internal.repository.XarInstalledExtension;
import org.xwiki.job.Job;
import org.xwiki.job.JobManager;
import org.xwiki.logging.LogLevel;
import org.xwiki.logging.event.LogEvent;
import org.xwiki.model.reference.DocumentReference;
import org.xwiki.observation.ObservationManager;

import com.xpn.xwiki.XWiki;
import com.xpn.xwiki.XWikiContext;
import com.xpn.xwiki.doc.XWikiDocument;
import com.xpn.xwiki.objects.BaseObject;
import com.xpn.xwiki.objects.classes.BaseClass;
import com.xpn.xwiki.objects.classes.NumberClass;
import com.xpn.xwiki.store.XWikiStoreInterface;
import com.xpn.xwiki.test.AbstractBridgedComponentTestCase;
import com.xpn.xwiki.util.XWikiStubContextProvider;

public class XarExtensionHandlerTest extends AbstractBridgedComponentTestCase
{
    private XWiki mockXWiki;

    private XWikiStoreInterface mockStore;

    private Map<DocumentReference, Map<String, XWikiDocument>> documents =
        new HashMap<DocumentReference, Map<String, XWikiDocument>>();

    private ExtensionId localXarExtensiontId1;

    private ExtensionId localXarExtensiontId2;

    private RepositoryUtil repositoryUtil;

    private JobManager taskManager;

    private InstalledExtensionRepository xarExtensionRepository;

    private Map<String, BaseClass> classes = new HashMap<String, BaseClass>();

    private DocumentReference contextUser;

    @Override
    @Before
    public void setUp() throws Exception
    {
        super.setUp();

        this.repositoryUtil = new RepositoryUtil(getComponentManager(), getMockery());
        this.repositoryUtil.setup();

        // mock

        this.mockXWiki = getMockery().mock(XWiki.class);
        getContext().setWiki(this.mockXWiki);
        getContext().setDatabase("xwiki");
        this.contextUser = new DocumentReference(getContext().getDatabase(), "XWiki", "ExtensionUser");

        this.mockStore = getMockery().mock(XWikiStoreInterface.class);

        this.localXarExtensiontId1 = new ExtensionId("test", "1.0");
        this.localXarExtensiontId2 = new ExtensionId("test", "2.0");

        // classes

        BaseClass styleSheetClass = new BaseClass();
        this.classes.put("StyleSheetExtension", styleSheetClass);

        // checking

        getMockery().checking(new Expectations()
        {
            {
                allowing(mockXWiki).getDocument(with(any(DocumentReference.class)), with(any(XWikiContext.class)));
                will(new CustomAction("getDocument")
                {
                    @Override
                    public Object invoke(org.jmock.api.Invocation invocation) throws Throwable
                    {
                        Map<String, XWikiDocument> documentLanguages = documents.get(invocation.getParameter(0));

                        if (documentLanguages == null) {
                            documentLanguages = new HashMap<String, XWikiDocument>();
                            documents.put((DocumentReference) invocation.getParameter(0), documentLanguages);
                        }

                        XWikiDocument document = documentLanguages.get("en");

                        if (document == null) {
                            document = new XWikiDocument((DocumentReference) invocation.getParameter(0));
                        }

                        return document;
                    }
                });

                allowing(mockStore).loadXWikiDoc(with(any(XWikiDocument.class)), with(any(XWikiContext.class)));
                will(new CustomAction("loadXWikiDoc")
                {
                    @Override
                    public Object invoke(org.jmock.api.Invocation invocation) throws Throwable
                    {
                        XWikiDocument providedDocument = (XWikiDocument) invocation.getParameter(0);
                        Map<String, XWikiDocument> documentLanguages =
                            documents.get(providedDocument.getDocumentReference());

                        if (documentLanguages == null) {
                            documentLanguages = new HashMap<String, XWikiDocument>();
                            documents.put((DocumentReference) invocation.getParameter(0), documentLanguages);
                        }

                        XWikiDocument document = documentLanguages.get(providedDocument.getRealLanguage());

                        if (document == null) {
                            document = new XWikiDocument(providedDocument.getDocumentReference());
                            document.setLanguage(providedDocument.getLanguage());
                            document.setDefaultLanguage(providedDocument.getDefaultLanguage());
                            document.setTranslation(providedDocument.getTranslation());
                        }

                        return document;
                    }
                });

                allowing(mockXWiki).saveDocument(with(any(XWikiDocument.class)), with(any(String.class)),
                    with(any(XWikiContext.class)));
                will(new CustomAction("saveDocument")
                {
                    @Override
                    public Object invoke(org.jmock.api.Invocation invocation) throws Throwable
                    {
                        XWikiDocument document = (XWikiDocument) invocation.getParameter(0);

                        document.incrementVersion();
                        document.setNew(false);

                        Map<String, XWikiDocument> documentLanguages = documents.get(document.getDocumentReference());

                        if (documentLanguages == null) {
                            documentLanguages = new HashMap<String, XWikiDocument>();
                            documents.put(document.getDocumentReference(), documentLanguages);
                        }

                        documentLanguages.put(document.getRealLanguage(), document);

                        return null;
                    }
                });

                allowing(mockXWiki).deleteDocument(with(any(XWikiDocument.class)), with(any(XWikiContext.class)));
                will(new CustomAction("deleteDocument")
                {
                    @Override
                    public Object invoke(org.jmock.api.Invocation invocation) throws Throwable
                    {
                        XWikiDocument document = (XWikiDocument) invocation.getParameter(0);

                        Map<String, XWikiDocument> documentLanguages = documents.get(document.getDocumentReference());

                        if (documentLanguages != null) {
                            documentLanguages.remove(document.getRealLanguage());
                        }

                        return null;
                    }
                });

                allowing(mockXWiki).getXClass(with(any(DocumentReference.class)), with(any(XWikiContext.class)));
                will(new CustomAction("getXClass")
                {
                    @Override
                    public Object invoke(org.jmock.api.Invocation invocation) throws Throwable
                    {
                        DocumentReference documentReference = (DocumentReference) invocation.getParameter(0);

                        return classes.get(documentReference.getName());
                    }
                });

                allowing(mockXWiki).isVirtualMode();
                will(returnValue(true));

                allowing(mockXWiki).getStore();
                will(returnValue(mockStore));

                allowing(mockXWiki).prepareResources(with(any(XWikiContext.class)));
            }
        });

        getContext().setUserReference(this.contextUser);

        ((XWikiStubContextProvider) getComponentManager().getInstance(XWikiStubContextProvider.class))
            .initialize(getContext());

        // lookup

        this.taskManager = getComponentManager().getInstance(JobManager.class);
        this.xarExtensionRepository = getComponentManager().getInstance(InstalledExtensionRepository.class, "xar");

        // Get rid of wiki macro listener
        getComponentManager().<ObservationManager> getInstance(ObservationManager.class).removeListener(
            "RegisterMacrosOnImportListener");
    }

    private XarInstalledExtension install(ExtensionId extensionId, String wiki) throws Throwable
    {
        InstallRequest installRequest = new InstallRequest();
        installRequest.setProperty("user.reference", getContext().getUserReference());
        installRequest.addExtension(extensionId);
        if (wiki != null) {
            installRequest.addNamespace("wiki:" + wiki);
        }
        Job installJob = this.taskManager.executeJob(InstallJob.JOBTYPE, installRequest);

        List<LogEvent> errors = installJob.getStatus().getLog().getLogs(LogLevel.ERROR);
        if (!errors.isEmpty()) {
            throw errors.get(0).getThrowable();
        }

        return (XarInstalledExtension) this.xarExtensionRepository.resolve(extensionId);
    }

    private void uninstall(ExtensionId extensionId, String wiki) throws Throwable
    {
        UninstallRequest uninstallRequest = new UninstallRequest();
        uninstallRequest.setProperty("user.reference", getContext().getUserReference());
        uninstallRequest.addExtension(extensionId);
        if (wiki != null) {
            uninstallRequest.addNamespace("wiki:" + wiki);
        }
        Job uninstallJob = this.taskManager.executeJob(UninstallJob.JOBTYPE, uninstallRequest);

        List<LogEvent> errors = uninstallJob.getStatus().getLog().getLogs(LogLevel.ERROR);
        if (!errors.isEmpty()) {
            throw errors.get(0).getThrowable();
        }
    }

    // Tests

    @Test
    public void testInstallOnWiki() throws Throwable
    {
        XWikiDocument existingDocument = new XWikiDocument(new DocumentReference("wiki", "space", "page"));
        existingDocument.setDefaultLanguage("en");
        existingDocument.setTranslation(0);
        existingDocument.setNew(false);
        existingDocument.setVersion("1.1");
        BaseObject object = new BaseObject();
        object.setXClassReference(new DocumentReference("wiki", "space", "object"));
        existingDocument.addXObject(object);
        Map<String, XWikiDocument> documentMap = new HashMap<String, XWikiDocument>();
        documentMap.put(existingDocument.getRealLanguage(), existingDocument);
        this.documents.put(existingDocument.getDocumentReference(), documentMap);

        // install

        install(this.localXarExtensiontId1, "wiki");

        // validate

        // space.page
        XWikiDocument page = this.mockXWiki.getDocument(existingDocument.getDocumentReference(), getContext());

        Assert.assertFalse("Document wiki:space.page has not been saved in the database", page.isNew());

        Assert.assertNull(page.getXObject(object.getXClassReference()));

        Assert.assertEquals("Wrong content", "content", page.getContent());
        Assert.assertEquals("Wrong author", this.contextUser, page.getAuthorReference());
        Assert.assertEquals("Wrong versions", "2.1", page.getVersion());

        BaseClass baseClass = page.getXClass();
        Assert.assertNotNull(baseClass.getField("property"));
        Assert.assertEquals("property", baseClass.getField("property").getName());
        Assert.assertSame(NumberClass.class, baseClass.getField("property").getClass());

        // space1.page1

        XWikiDocument page1 =
            this.mockXWiki.getDocument(new DocumentReference("wiki", "space1", "page1"), getContext());

        Assert.assertFalse("Document wiki:space1.page1 has not been saved in the database", page1.isNew());

        // translated.translated.tr
        DocumentReference translatedReference = new DocumentReference("wiki", "translated", "translated");
        XWikiDocument translated = this.documents.get(translatedReference).get("tr");

        Assert.assertNotNull("Document wiki:space.page has not been saved in the database", translated);
        Assert.assertFalse("Document wiki:space.page has not been saved in the database", translated.isNew());

        Assert.assertEquals("Wrong content", "translated content", translated.getContent());
        Assert.assertEquals("Wrong author", this.contextUser, translated.getAuthorReference());
        Assert.assertEquals("Wrong versions", "1.1", translated.getVersion());
    }

    @Test
    public void testUpgradeOnWiki() throws Throwable
    {
        install(this.localXarExtensiontId1, "wiki");

        // upgrade

        install(this.localXarExtensiontId2, "wiki");

        // validate

        XWikiDocument samepage = this.mockXWiki.getDocument(new DocumentReference("wiki", "samespace", "samepage"), getContext());

        Assert.assertEquals("Wrong versions", "1.1", samepage.getVersion());
        
        XWikiDocument modifiedpage = this.mockXWiki.getDocument(new DocumentReference("wiki", "space", "page"), getContext());

        Assert.assertFalse("Document wiki.space.page has not been saved in the database", modifiedpage.isNew());

        Assert.assertEquals("Wrong content", "content 2", modifiedpage.getContent());
        Assert.assertEquals("Wrong author", this.contextUser, modifiedpage.getAuthorReference());
        Assert.assertEquals("Wrong versions", "2.1", modifiedpage.getVersion());

        BaseClass baseClass = modifiedpage.getXClass();
        Assert.assertNotNull(baseClass.getField("property"));
        Assert.assertEquals("property", baseClass.getField("property").getName());
        Assert.assertSame(NumberClass.class, baseClass.getField("property").getClass());

        XWikiDocument newPage =
            this.mockXWiki.getDocument(new DocumentReference("wiki", "space2", "page2"), getContext());

        Assert.assertFalse("Document wiki.space2.page2 has not been saved in the database", newPage.isNew());

        XWikiDocument removedPage =
            this.mockXWiki.getDocument(new DocumentReference("wiki", "space1", "page1"), getContext());

        Assert.assertTrue("Document wiki.space1.page1 has not been removed from the database", removedPage.isNew());
    }

    @Test
    public void testUninstallFromWiki() throws Throwable
    {
        install(this.localXarExtensiontId1, "wiki");

        // uninstall

        uninstall(this.localXarExtensiontId1, "wiki");

        // validate

        XWikiDocument page = this.mockXWiki.getDocument(new DocumentReference("wiki", "space", "page"), getContext());

        Assert.assertTrue("Document wiki.space.page has not been removed from the database", page.isNew());

        XWikiDocument page1 =
            this.mockXWiki.getDocument(new DocumentReference("wiki", "space1", "page1"), getContext());

        Assert.assertTrue("Document wiki.space1.page1 has not been removed from the database", page1.isNew());
    }

    @Test
    public void testInstallOnRoot() throws Throwable
    {
        getMockery().checking(new Expectations()
        {
            {
                allowing(mockXWiki).getVirtualWikisDatabaseNames(with(any(XWikiContext.class)));
                will(returnValue(Arrays.asList("wiki1", "wiki2")));
            }
        });

        // install

        install(this.localXarExtensiontId1, null);

        // validate

        XWikiDocument pageWiki1 =
            this.mockXWiki.getDocument(new DocumentReference("wiki1", "space1", "page1"), getContext());

        Assert.assertFalse(pageWiki1.isNew());

        XWikiDocument pageWiki2 =
            this.mockXWiki.getDocument(new DocumentReference("wiki2", "space1", "page1"), getContext());

        Assert.assertFalse(pageWiki2.isNew());

        // uninstall

        uninstall(this.localXarExtensiontId1, null);

        // validate

        pageWiki1 = this.mockXWiki.getDocument(new DocumentReference("wiki1", "space1", "page1"), getContext());

        Assert.assertTrue(pageWiki1.isNew());

        pageWiki2 = this.mockXWiki.getDocument(new DocumentReference("wiki2", "space1", "page1"), getContext());

        Assert.assertTrue(pageWiki2.isNew());
    }
}
