/*
 * (C) Copyright 2020 Nuxeo (http://nuxeo.com/) and others.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 *     Anahide Tchertchian
 */
package org.nuxeo.apidoc.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.apidoc.api.BundleInfo;
import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.apidoc.api.ExtensionPointInfo;
import org.nuxeo.apidoc.plugin.Plugin;
import org.nuxeo.apidoc.plugin.PluginSnapshot;
import org.nuxeo.apidoc.snapshot.DistributionSnapshot;
import org.nuxeo.apidoc.snapshot.SnapshotManager;
import org.nuxeo.runtime.test.runner.Deploy;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

import com.fasterxml.jackson.core.JsonGenerationException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * @since 11.1
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeSnaphotFeature.class)
@Deploy("org.nuxeo.apidoc.repo:apidoc-plugin-test-contrib.xml")
public class TestPlugin {

    @Inject
    protected SnapshotManager snapshotManager;

    @Test
    public void testRegistration() {
        Plugin<?> foo = snapshotManager.getPlugin("foo");
        assertNull(foo);

        Plugin<?> p = snapshotManager.getPlugin("testPlugin");
        assertNotNull(p);
    }

    @Test
    public void testPlugins() {
        List<Plugin<?>> plugins = snapshotManager.getPlugins();
        assertNotNull(plugins);
        assertEquals(1, plugins.size());
    }

    @Test
    public void testPlugin() {
        Plugin<?> p = snapshotManager.getPlugin("testPlugin");
        assertNotNull(p);
        assertTrue(p instanceof FakePlugin);
        assertEquals("testPlugin", p.getId());
        assertEquals(FakePluginRuntimeSnapshot.class.getCanonicalName(), p.getPluginSnapshotClass());
        assertEquals("myType", p.getViewType());
        assertEquals("My snapshot plugin", p.getLabel());
        assertEquals("listItems", p.getHomeView());
        assertEquals("myStyleClass", p.getStyleClass());
        assertFalse(p.isHidden());
    }

    @Test
    public void testPluginRuntimeSnapshot() {
        DistributionSnapshot snapshot = snapshotManager.getRuntimeSnapshot();
        PluginSnapshot<?> psnap = snapshot.getPluginSnapshots().get(FakePlugin.ID);
        assertNotNull(psnap);
        assertTrue(psnap instanceof FakePluginRuntimeSnapshot);
        checkPluginRuntimeSnapshot(snapshot, (FakePluginRuntimeSnapshot) psnap);
    }

    @Test
    public void testPluginJson() throws JsonGenerationException, JsonMappingException, IOException {
        DistributionSnapshot snapshot = snapshotManager.getRuntimeSnapshot();

        // write to output stream
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        snapshot.writeJson(out);

        // XXX
        try (OutputStream file = Files.newOutputStream(Paths.get(FeaturesRunner.getBuildDirectory() + "/test.json"),
                StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            file.write(out.toByteArray());
        }

        // read back and explore plugin resources again
        ByteArrayInputStream source = new ByteArrayInputStream(out.toByteArray());
        DistributionSnapshot rsnap = snapshot.readJson(source);

        PluginSnapshot<?> psnap = rsnap.getPluginSnapshots().get(FakePlugin.ID);
        assertNotNull(psnap);
        assertTrue(psnap instanceof FakePluginRuntimeSnapshot);
        checkPluginRuntimeSnapshot(rsnap, (FakePluginRuntimeSnapshot) psnap);
    }

    /**
     * Test a legacy NuxeoArtifact an still be resolved thanks to this old-exported json, that can also serve a
     * json-comaptibility test for the whole json, not only with plugins.
     */
    @Test
    public void testPluginJsonLegacy() throws JsonGenerationException, JsonMappingException, IOException {
        String export = TestSnapshotPersist.getReferenceContent(
                TestSnapshotPersist.getReferencePath("plugin-test-export.json"));

        // read back and explore plugin resources again
        ByteArrayInputStream source = new ByteArrayInputStream(export.getBytes());
        // retrieve current snapshot just to get the reader...
        DistributionSnapshot snapshot = snapshotManager.getRuntimeSnapshot();
        DistributionSnapshot rsnap = snapshot.readJson(source);

        PluginSnapshot<?> psnap = rsnap.getPluginSnapshots().get(FakePlugin.ID);
        assertNotNull(psnap);
        assertTrue(psnap instanceof FakePluginRuntimeSnapshot);
        checkPluginRuntimeSnapshot(rsnap, (FakePluginRuntimeSnapshot) psnap);
    }

    protected void checkPluginRuntimeSnapshot(DistributionSnapshot snapshot, FakePluginRuntimeSnapshot psnapshot) {
        List<String> itemIds = psnapshot.getItemIds();
        assertNotNull(itemIds);
        assertEquals(3, itemIds.size());
        assertEquals("org.nuxeo.apidoc.core", itemIds.get(0));
        assertEquals("org.nuxeo.apidoc.adapterContrib", itemIds.get(1));
        assertEquals("org.nuxeo.apidoc.snapshot.SnapshotManagerComponent--plugins", itemIds.get(2));

        // get introspected version from one of the bundles (e.g. 11.1-SNAPSHOT when writing this test)
        String version = snapshot.getBundle(snapshot.getBundleIds().get(0)).getArtifactVersion();

        FakeNuxeoArtifact item = psnapshot.getItem(itemIds.get(0));
        assertNotNull(item);
        assertEquals("org.nuxeo.apidoc.core", item.getId());
        assertEquals(BundleInfo.TYPE_NAME, item.getArtifactType());
        assertEquals(version, item.getVersion());

        item = psnapshot.getItem(itemIds.get(1));
        assertNotNull(item);
        assertEquals("org.nuxeo.apidoc.adapterContrib", item.getId());
        assertEquals(ComponentInfo.TYPE_NAME, item.getArtifactType());
        assertEquals(version, item.getVersion());

        item = psnapshot.getItem(itemIds.get(2));
        assertNotNull(item);
        assertEquals("org.nuxeo.apidoc.snapshot.SnapshotManagerComponent--plugins", item.getId());
        assertEquals(ExtensionPointInfo.TYPE_NAME, item.getArtifactType());
        assertEquals(version, item.getVersion());
    }

}
