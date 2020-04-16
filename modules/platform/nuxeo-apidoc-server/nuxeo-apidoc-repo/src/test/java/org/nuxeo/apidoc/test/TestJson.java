/*
 * (C) Copyright 2012-2013 Nuxeo SA (http://nuxeo.com/) and others.
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
 */
package org.nuxeo.apidoc.test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.Collection;

import javax.inject.Inject;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.nuxeo.apidoc.api.BundleInfo;
import org.nuxeo.apidoc.api.ComponentInfo;
import org.nuxeo.apidoc.api.ExtensionPointInfo;
import org.nuxeo.apidoc.api.ServiceInfo;
import org.nuxeo.apidoc.introspection.RuntimeSnapshot;
import org.nuxeo.apidoc.snapshot.DistributionSnapshot;
import org.nuxeo.apidoc.snapshot.SnapshotManager;
import org.nuxeo.ecm.core.api.CoreSession;
import org.nuxeo.runtime.test.runner.Features;
import org.nuxeo.runtime.test.runner.FeaturesRunner;

/**
 * @since 8.3
 */
@RunWith(FeaturesRunner.class)
@Features(RuntimeSnaphotRepoFeature.class)
public class TestJson {

    @Inject
    protected CoreSession session;

    @Inject
    protected SnapshotManager snapshotManager;

    @Test
    public void canSerializeRuntimeAndReadBack() throws IOException {
        DistributionSnapshot snapshot = RuntimeSnapshot.build();
        assertNotNull(snapshot);
        canSerializeAndReadBack(snapshot);
    }

    @Test
    public void canSerializeRepositoryAndReadBack() throws IOException {
        DistributionSnapshot snapshot = snapshotManager.persistRuntimeSnapshot(session);
        assertNotNull(snapshot);
        canSerializeAndReadBack(snapshot);
    }

    protected void canSerializeAndReadBack(DistributionSnapshot snap) throws IOException {
        try (ByteArrayOutputStream sink = new ByteArrayOutputStream()) {
            snap.writeJson(sink);
            try (OutputStream file = Files.newOutputStream(Paths.get(FeaturesRunner.getBuildDirectory() + "/test.json"),
                    StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
                file.write(sink.toByteArray());
            }
            try (ByteArrayInputStream source = new ByteArrayInputStream(sink.toByteArray())) {
                DistributionSnapshot snapshot = snap.readJson(source);
                assertNotNull(snapshot);
                assertNotNull(snapshot.getBundle("org.nuxeo.apidoc.repo"));
            }
        }
    }

    /**
     * Reads a reference export kept in tests, to detect potential compatibility changes.
     *
     * @since 11.1
     */
    @Test
    public void canReadLegacy() throws IOException {
        RuntimeSnapshot runtimeSnapshot = RuntimeSnapshot.build();
        String export = TestSnapshotPersist.getReferenceContent(
                TestSnapshotPersist.getReferencePath("test-export.json"));
        try (ByteArrayInputStream source = new ByteArrayInputStream(export.getBytes())) {
            DistributionSnapshot snapshot = runtimeSnapshot.readJson(source);
            assertNotNull(snapshot);

            BundleInfo bundle = snapshot.getBundle("org.nuxeo.apidoc.repo");
            assertNotNull(bundle);
            assertEquals("nuxeo-apidoc-repo", bundle.getArtifactId());
            assertEquals(BundleInfo.TYPE_NAME, bundle.getArtifactType());
            assertEquals("11.1-SNAPSHOT", bundle.getArtifactVersion());
            assertEquals("org.nuxeo.apidoc.repo", bundle.getBundleId());
            assertEquals("org.nuxeo.ecm.platform", bundle.getGroupId());
            assertEquals("/grp:org.nuxeo.ecm.platform/org.nuxeo.apidoc.repo", bundle.getHierarchyPath());
            assertEquals("org.nuxeo.apidoc.repo", bundle.getId());
            assertEquals("/home/anahide/ws/nuxeo/modules/platform/nuxeo-apidoc-server/nuxeo-apidoc-repo/bin/main",
                    bundle.getLocation());
            assertEquals("Manifest-Version: 1.0\n" //
                    + "Bundle-ManifestVersion: 1\n" //
                    + "Bundle-Name: nuxeo api documentation repository\n" //
                    + "Bundle-SymbolicName: org.nuxeo.apidoc.repo;singleton:=true\n" //
                    + "Bundle-Version: 0.0.1\n" //
                    + "Bundle-Vendor: Nuxeo\n" //
                    + "Nuxeo-Component: OSGI-INF/schema-contrib.xml,\n" //
                    + "  OSGI-INF/doctype-contrib.xml,\n" + "  OSGI-INF/life-cycle-contrib.xml,\n" //
                    + "  OSGI-INF/snapshot-service-framework.xml,\n" //
                    + "  OSGI-INF/documentation-service-framework.xml,\n" //
                    + "  OSGI-INF/adapter-contrib.xml,\n" //
                    + "  OSGI-INF/directories-contrib.xml,\n" //
                    + "  OSGI-INF/listener-contrib.xml\n"//
                    + "", bundle.getManifest());
            // retrieve one sample of each contribution
            Collection<ComponentInfo> components = bundle.getComponents();
            assertNotNull(components);
            assertEquals(9, components.size());
            ComponentInfo smcomp = snapshot.getComponent("org.nuxeo.apidoc.snapshot.SnapshotManagerComponent");
            assertNotNull(smcomp);
            // check managed reference
            assertNotNull(smcomp.getBundle());
            assertEquals("org.nuxeo.apidoc.repo", smcomp.getBundle().getId());
            // check services
            assertNotNull(smcomp.getServices());
            assertEquals(1, smcomp.getServices().size());
            ServiceInfo service = smcomp.getServices().get(0);
            // check managed reference
            assertNotNull(smcomp.getBundle());
            // check extension points
            assertNotNull(smcomp.getExtensionPoints());
            assertEquals(1, smcomp.getExtensionPoints().size());
            ExtensionPointInfo xp = smcomp.getExtensionPoints().iterator().next();
            // check managed reference
            assertNotNull(smcomp.getBundle());
            // check extensions
            assertNotNull(smcomp.getExtensions());
            assertEquals(0, smcomp.getExtensions().size());

            // check another component with contributions
            ComponentInfo smcont = snapshot.getComponent("org.nuxeo.apidoc.doctypeContrib");
            assertNotNull(smcont);
            // check extensions
            assertNotNull(smcont.getExtensions());
            assertEquals(1, smcont.getExtensions().size());
            // check managed reference
            assertNotNull(smcomp.getBundle());

        }
    }

}
