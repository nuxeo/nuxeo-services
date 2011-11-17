/*
 * (C) Copyright 2006-2007 Nuxeo SAS (http://nuxeo.com/) and contributors.
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Lesser General Public License
 * (LGPL) version 2.1 which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/lgpl.html
 *
 * This library is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * Contributors:
 *     Nuxeo - initial API and implementation
 *
 * $Id: JOOoConvertPluginImpl.java 18651 2007-05-13 20:28:53Z sfermigier $
 */

package org.nuxeo.ecm.platform.filemanager;

import java.io.File;
import java.util.List;

import org.nuxeo.common.utils.FileUtils;
import org.nuxeo.ecm.core.api.ClientException;
import org.nuxeo.ecm.core.api.CoreInstance;
import org.nuxeo.ecm.core.api.DocumentModel;
import org.nuxeo.ecm.core.api.DocumentRef;
import org.nuxeo.ecm.core.api.impl.blob.ByteArrayBlob;
import org.nuxeo.ecm.core.event.EventService;
import org.nuxeo.ecm.core.repository.jcr.testing.RepositoryOSGITestCase;
import org.nuxeo.ecm.platform.filemanager.api.FileManager;
import org.nuxeo.ecm.platform.filemanager.service.FileManagerService;
import org.nuxeo.ecm.platform.filemanager.service.extension.FileImporter;
import org.nuxeo.ecm.platform.filemanager.utils.FileManagerUtils;
import org.nuxeo.runtime.api.Framework;

public class TestFileManagerService extends RepositoryOSGITestCase {

    protected FileManager service;

    protected DocumentModel root;

    protected DocumentModel workspace;

    @Override
    public void setUp() throws Exception {
        super.setUp();

        deployBundle(FileManagerUTConstants.TYPESERVICE_BUNDLE);
        deployContrib(FileManagerUTConstants.FILEMANAGER_TEST_BUNDLE,
                "ecm-types-test-contrib.xml");
        deployBundle(FileManagerUTConstants.MIMETYPE_BUNDLE);

        deployContrib(FileManagerUTConstants.FILEMANAGER_BUNDLE,
                "OSGI-INF/nxfilemanager-service.xml");
        deployContrib(FileManagerUTConstants.FILEMANAGER_BUNDLE,
                "OSGI-INF/nxfilemanager-plugins-contrib.xml");

        deployContrib(FileManagerUTConstants.FILEMANAGER_TEST_BUNDLE,
                "nxfilemanager-test-contribs.xml");

        openRepository();
        service = Framework.getService(FileManager.class);
        root = coreSession.getRootDocument();
        createWorkspaces();
    }

    private void createWorkspaces() throws ClientException {
        DocumentModel workspace = coreSession.createDocumentModel(root.getPathAsString(), "workspace", "Workspace");
        coreSession.createDocument(workspace);
        this.workspace = workspace;
    }

    @Override
    public void tearDown() throws Exception {
        Framework.getLocalService(EventService.class).waitForAsyncCompletion();
        if (coreSession != null) {
            CoreInstance.getInstance().close(coreSession);
            coreSession = null;
        }
        service = null;
        root = null;

        undeployContrib(FileManagerUTConstants.FILEMANAGER_TEST_BUNDLE,
                "nxfilemanager-test-contribs.xml");

        undeployContrib(FileManagerUTConstants.FILEMANAGER_BUNDLE,
                "OSGI-INF/nxfilemanager-plugins-contrib.xml");
        undeployContrib(FileManagerUTConstants.FILEMANAGER_BUNDLE,
                "OSGI-INF/nxfilemanager-service.xml");
        // undeployBundle(TestConstants.MIMETYPE_BUNDLE);
        undeployContrib(FileManagerUTConstants.FILEMANAGER_TEST_BUNDLE,
                "ecm-types-test-contrib.xml");
        // undeployBundle(TestConstants.TYPESERVICE_BUNDLE);

        super.tearDown();
    }

    protected File getTestFile(String relativePath) {
        return new File(FileUtils.getResourcePathFromContext(relativePath));
    }

    public void testDefaultCreateFromBlob() throws Exception {
        File file = getTestFile("test-data/hello.doc");

        byte[] content = FileManagerUtils.getBytesFromFile(file);
        ByteArrayBlob input = new ByteArrayBlob(content, "application/msword");

        DocumentModel doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.doc");
        assertNotNull(doc);
        assertEquals("hello.doc", doc.getProperty("dublincore", "title"));
        assertEquals("hello.doc", doc.getProperty("file", "filename"));
        assertNotNull(doc.getProperty("file", "content"));
    }

    public void testDefaultCreateTwiceFromSameBlob() throws Exception {
        // create doc
        File file = getTestFile("test-data/hello.doc");

        byte[] content = FileManagerUtils.getBytesFromFile(file);
        ByteArrayBlob input = new ByteArrayBlob(content, "application/msword");

        DocumentModel doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.doc");
        DocumentRef docRef = doc.getRef();

        assertNotNull(doc);
        assertEquals("hello.doc", doc.getProperty("dublincore", "title"));
        assertEquals("hello.doc", doc.getProperty("file", "filename"));
        assertNotNull(doc.getProperty("file", "content"));

        List<DocumentModel> versions = coreSession.getVersions(docRef);
        assertEquals(0, versions.size());

        // create again with same file
        doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.doc");
        assertNotNull(doc);

        DocumentRef newDocRef = doc.getRef();
        assertEquals(docRef, newDocRef);
        assertEquals("hello.doc", doc.getProperty("dublincore", "title"));
        assertEquals("hello.doc", doc.getProperty("file", "filename"));
        assertNotNull(doc.getProperty("file", "content"));

        versions = coreSession.getVersions(docRef);
        assertEquals(1, versions.size());
    }

    public void testDefaultUpdateFromBlob() throws Exception {
        // create doc
        File file = getTestFile("test-data/hello.doc");

        byte[] content = FileManagerUtils.getBytesFromFile(file);
        ByteArrayBlob input = new ByteArrayBlob(content, "application/msword");

        DocumentModel doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.doc");
        DocumentRef docRef = doc.getRef();

        assertNotNull(doc);
        assertEquals("hello.doc", doc.getProperty("dublincore", "title"));
        assertEquals("hello.doc", doc.getProperty("file", "filename"));
        assertNotNull(doc.getProperty("file", "content"));

        // update it with another file with same name
        doc = service.updateDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), "test-data/update/hello.doc");
        assertNotNull(doc);

        DocumentRef newDocRef = doc.getRef();
        assertEquals(docRef, newDocRef);
        assertEquals("hello.doc", doc.getProperty("dublincore", "title"));
        assertEquals("hello.doc", doc.getProperty("file", "filename"));
        assertNotNull(doc.getProperty("file", "content"));
    }

    protected static final String NOTE_HTML_CONTENT
            = "<html>\n<body>\n  <p>Hello from HTML document</p>\n</body>\n</html>";

    public void testCreateNote() throws Exception {
        File file = getTestFile("test-data/hello.html");

        byte[] content = FileManagerUtils.getBytesFromFile(file);
        ByteArrayBlob input = new ByteArrayBlob(content, "text/html");

        DocumentModel doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.html");
        assertNotNull(doc);
        assertEquals("hello.html", doc.getProperty("dublincore", "title"));
        assertEquals(NOTE_HTML_CONTENT, doc.getProperty("note", "note"));
    }

    public void testCreateNoteTwiceFromSameBlob() throws Exception {
        // create doc
        File file = getTestFile("test-data/hello.html");

        byte[] content = FileManagerUtils.getBytesFromFile(file);
        ByteArrayBlob input = new ByteArrayBlob(content, "text/html");

        DocumentModel doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.html");
        DocumentRef docRef = doc.getRef();

        assertNotNull(doc);
        assertEquals("hello.html", doc.getProperty("dublincore", "title"));
        assertEquals(NOTE_HTML_CONTENT, doc.getProperty("note", "note"));

        List<DocumentModel> versions = coreSession.getVersions(docRef);
        assertEquals(0, versions.size());

        // create again with same file
        doc = service.createDocumentFromBlob(coreSession, input,
                workspace.getPathAsString(), true, "test-data/hello.html");
        assertNotNull(doc);
        DocumentRef newDocRef = doc.getRef();
        assertEquals(docRef, newDocRef);
        assertEquals("hello.html", doc.getProperty("dublincore", "title"));
        assertEquals(NOTE_HTML_CONTENT, doc.getProperty("note", "note"));

        versions = coreSession.getVersions(docRef);
        assertEquals(1, versions.size());
    }

    public void testFileImporterDocType() {
        FileManagerService fileManagerService = (FileManagerService) service;
        FileImporter plugin = fileManagerService.getPluginByName("plug");
        assertNull(plugin.getDocType());

        plugin = fileManagerService.getPluginByName("pluginWithDocType");
        assertNotNull(plugin.getDocType());
        assertEquals("File", plugin.getDocType());
    }

    public void testFileImportersMerge() throws Exception {
        deployContrib(FileManagerUTConstants.FILEMANAGER_TEST_BUNDLE,
                        "nxfilemanager-test-override.xml");

        FileManagerService fileManagerService = (FileManagerService) service;

        FileImporter plugin = fileManagerService.getPluginByName("pluginWithDocType");
        assertNotNull(plugin.getDocType());
        assertEquals("Picture", plugin.getDocType());
        assertEquals(2, plugin.getFilters().size());
        List<String> filters = plugin.getFilters();
        assertTrue(filters.contains("image/jpeg"));
        assertTrue(filters.contains("image/png"));

        plugin = fileManagerService.getPluginByName("plug");
        assertNotNull(plugin.getDocType());
        assertEquals("Note", plugin.getDocType());
        assertEquals(3, plugin.getFilters().size());
        filters = plugin.getFilters();
        assertTrue(filters.contains("text/plain"));
        assertTrue(filters.contains("text/rtf"));
        assertTrue(filters.contains("text/xml"));
    }

}
