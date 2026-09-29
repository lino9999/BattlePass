package com.Lino.battlePass;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

class PluginMetadataTest {
    @Test
    void packagedDescriptorUsesTheMavenReleaseVersion() throws Exception {
        var pom = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new File("pom.xml"));
        String releaseVersion = pom.getDocumentElement().getElementsByTagName("version").item(0).getTextContent();
        try (var resource = getClass().getResourceAsStream("/plugin.yml")) {
            assertNotNull(resource);
            var descriptor = YamlConfiguration.loadConfiguration(new InputStreamReader(resource, StandardCharsets.UTF_8));
            assertEquals(releaseVersion, descriptor.getString("version"));
        }
    }
}
