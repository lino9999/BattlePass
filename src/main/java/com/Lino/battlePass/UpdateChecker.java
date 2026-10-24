package com.Lino.battlePass;

import com.Lino.battlePass.utils.VersionUtils;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;

/** Blocking network work only; the plugin publishes the result on its current server-thread lifecycle. */
final class UpdateChecker {
    static final String RESOURCE_ID = "125992";

    private UpdateChecker() {}

    record Result(String version, boolean newer) {}

    static Result check(String installed) throws IOException, URISyntaxException {
        URI uri = new URI("https://api.spigotmc.org/legacy/update.php?resource=" + RESOURCE_ID);
        HttpURLConnection connection = (HttpURLConnection) uri.toURL().openConnection();
        try {
            connection.setRequestMethod("GET");
            connection.setConnectTimeout(5000);
            connection.setReadTimeout(5000);
            connection.setRequestProperty("User-Agent", "BattlePass-UpdateChecker");
            int status = connection.getResponseCode();
            if (status != 200) throw new IOException("Response code " + status);
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(connection.getInputStream(), StandardCharsets.UTF_8))) {
                String version = reader.readLine();
                if (version == null || version.isBlank()) return null;
                version = version.trim();
                if (!VersionUtils.isValidVersion(version)) throw new IOException("Invalid version response");
                return new Result(version, VersionUtils.isNewer(version, installed));
            }
        } finally {
            connection.disconnect();
        }
    }
}
