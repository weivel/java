package io.github.intisy.ai.seam.jvm.library;

import io.github.intisy.ai.engine.PluginException;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.CharacterCodingException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Fetches a pinned library's declaration and one platform's binary into a cache, verifying both.
 *
 * @implNote A cached file is hashed on every acquisition and never trusted for existing: a
 * mismatch downloads again, and a download that also mismatches is refused without being written.
 * The declaration is verified exactly like the binary, since a declaration names the symbols the
 * host is about to call.
 */
public final class LibraryAcquirer {

    /** A verified declaration and the verified binary it describes, both in the cache. */
    public static final class AcquiredLibrary {
        private final LibraryDeclaration declaration;
        private final Path binaryPath;

        AcquiredLibrary(LibraryDeclaration declaration, Path binaryPath) {
            this.declaration = declaration;
            this.binaryPath = binaryPath;
        }

        /** @return the library's verified declaration */
        public LibraryDeclaration declaration() {
            return declaration;
        }

        /** @return the verified binary for the acquirer's platform, inside the cache */
        public Path binaryPath() {
            return binaryPath;
        }
    }

    /** Fetches the bytes behind a URL; a test seam, a host uses {@link #overHttps()}. */
    interface Download {
        byte[] fetch(String url) throws IOException;
    }

    /** Moves a verified temporary file onto its final name; a test seam, a host uses {@link #atomicMove()}. */
    interface Move {
        void move(Path from, Path to) throws IOException;
    }

    private static final String DECLARATION_ASSET = "library.json";
    private static final String RELEASE_HOST = "https://github.com";
    private static final Pattern PATH_SEGMENT = Pattern.compile("^(?!\\.{1,2}$)[A-Za-z0-9_.+-]+$");
    private static final Pattern HEX_DIGEST = Pattern.compile("^[0-9a-fA-F]{64}$");
    private static final int CONNECT_TIMEOUT_MILLIS = 15000;
    private static final int READ_TIMEOUT_MILLIS = 60000;

    private final String platformKey;
    private final Download download;
    private final Move move;

    /** An acquirer for the platform this process runs on. */
    public LibraryAcquirer() {
        this(currentPlatformKey());
    }

    /** @param platformKey the binary to acquire, as a release names it, for example {@code linux-x64} */
    public LibraryAcquirer(String platformKey) {
        this(platformKey, overHttps(), atomicMove());
    }

    LibraryAcquirer(String platformKey, Download download, Move move) {
        this.platformKey = platformKey;
        this.download = download;
        this.move = move;
    }

    /**
     * The platform key this process loads binaries for, spelt as Node spells {@code process.platform}
     * and {@code process.arch}, which is how a release names its binaries.
     *
     * @return for example {@code win32-x64}, {@code linux-x64} or {@code darwin-arm64}
     */
    public static String currentPlatformKey() {
        return platformOf(System.getProperty("os.name", "")) + "-" + architectureOf(System.getProperty("os.arch", ""));
    }

    private static String platformOf(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return "win32";
        }
        if (name.startsWith("mac") || name.startsWith("darwin")) {
            return "darwin";
        }
        return name.startsWith("linux") ? "linux" : name.replaceAll("\\s+", "");
    }

    private static String architectureOf(String osArch) {
        String arch = osArch.toLowerCase(Locale.ROOT);
        if (arch.equals("amd64") || arch.equals("x86_64")) {
            return "x64";
        }
        if (arch.equals("aarch64") || arch.equals("arm64")) {
            return "arm64";
        }
        return arch.equals("x86") || arch.matches("i[3-6]86") ? "ia32" : arch;
    }

    /** @return the download a host uses: HTTPS GET, following GitHub's redirect to its asset store */
    static Download overHttps() {
        return LibraryAcquirer::httpsGet;
    }

    /** @return the move a host uses: an atomic rename that replaces what is there */
    static Move atomicMove() {
        return (from, to) -> Files.move(from, to, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
    }

    /**
     * @implNote {@code HttpURLConnection} follows a redirect only within one protocol, which is
     * enough here: a release download answers 302 from {@code github.com} to
     * {@code release-assets.githubusercontent.com}, https to https (measured 2026-10-26).
     */
    private static byte[] httpsGet(String url) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) new URL(url).openConnection();
        connection.setInstanceFollowRedirects(true);
        connection.setConnectTimeout(CONNECT_TIMEOUT_MILLIS);
        connection.setReadTimeout(READ_TIMEOUT_MILLIS);
        try {
            int status = connection.getResponseCode();
            if (status != HttpURLConnection.HTTP_OK) {
                throw new IOException("answered HTTP " + status);
            }
            try (InputStream body = connection.getInputStream()) {
                return readAll(body);
            }
        } finally {
            connection.disconnect();
        }
    }

    private static byte[] readAll(InputStream input) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[65536];
        for (int read = input.read(chunk); read != -1; read = input.read(chunk)) {
            buffer.write(chunk, 0, read);
        }
        return buffer.toByteArray();
    }

    /**
     * Fetches a pinned library's declaration and this acquirer's platform binary into the cache, verifying both.
     *
     * @param pin the release to acquire and the digests it must match
     * @param cacheDir the cache root; the release lands under {@code <owner>/<name>/<tag>} inside it
     * @return the verified declaration and the path of the verified binary
     * @throws PluginException naming the release when an asset is unpinned, unreachable, mismatched or unwritable
     */
    public AcquiredLibrary acquire(LibraryPin pin, Path cacheDir) {
        assertPinShape(pin);
        String binaryDigest = pinnedDigest(pin, platformKey, "add the " + platformKey
                + " digest from the release's SHA256SUMS to the pin, or run on a platform the library is built for");
        String declarationDigest = pinnedDigest(pin, DECLARATION_ASSET, "add the library.json digest from the release's SHA256SUMS to the pin");
        String[] repository = pin.repository().split("/");
        Path directory = cacheDir.resolve(repository[0]).resolve(repository[1]).resolve(pin.tag());
        LibraryDeclaration declaration = declarationOf(pin, verifiedAsset(pin, directory, DECLARATION_ASSET, declarationDigest).bytes);
        Path binary = verifiedAsset(pin, directory, binaryAssetFor(pin, declaration), binaryDigest).path;
        return new AcquiredLibrary(declaration, binary);
    }

    private static PluginException refusal(LibraryPin pin, String detail, String fix) {
        return new PluginException(pin.label(), detail, fix);
    }

    /** @implNote The repository and tag become cache path segments, so neither may climb out of the cache. */
    private static void assertPinShape(LibraryPin pin) {
        String[] repository = pin.repository() == null ? new String[0] : pin.repository().split("/", -1);
        boolean wellFormed = repository.length == 2 && pin.tag() != null
                && isPathSegment(repository[0]) && isPathSegment(repository[1]) && isPathSegment(pin.tag());
        if (!wellFormed) {
            throw refusal(pin, "the pin's repository must be owner/name and its tag a plain release tag",
                    "pin the library as new LibraryPin(\"owner/name\", \"v1.2.3\", digests)");
        }
    }

    private static boolean isPathSegment(String segment) {
        return PATH_SEGMENT.matcher(segment).matches();
    }

    private static String pinnedDigest(LibraryPin pin, String key, String missingFix) {
        String digest = pin.sha256().get(key);
        if (digest != null && HEX_DIGEST.matcher(digest).matches()) {
            return digest.toLowerCase(Locale.ROOT);
        }
        throw refusal(pin, "the pin carries no SHA-256 for " + key + ", so " + pin.label() + " has nothing verifiable to load for it", missingFix);
    }

    private String binaryAssetFor(LibraryPin pin, LibraryDeclaration declaration) {
        String platform = platformKey.split("-")[0];
        String extension = platform.equals("win32") ? "dll" : platform.equals("linux") ? "so" : platform.equals("darwin") ? "dylib" : null;
        if (extension == null) {
            throw refusal(pin, "no shared-library format is known for platform " + platformKey, "run the host on Windows, Linux or macOS");
        }
        return declaration.binary() + "-" + platformKey + "." + extension;
    }

    private static final class Asset {
        private final Path path;
        private final byte[] bytes;

        Asset(Path path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    private Asset verifiedAsset(LibraryPin pin, Path directory, String asset, String digest) {
        Path path = directory.resolve(asset);
        byte[] cached = readIfPresent(path);
        if (cached != null && sha256Of(cached).equals(digest)) {
            return new Asset(path, cached);
        }
        String url = RELEASE_HOST + "/" + pin.repository() + "/releases/download/" + pin.tag() + "/" + asset;
        byte[] downloaded = downloaded(pin, url);
        String actual = sha256Of(downloaded);
        if (!actual.equals(digest)) {
            throw refusal(pin, asset + " from " + url + " has SHA-256 " + actual + ", but the pin says " + digest,
                    "never load it: correct the pin only if the release was rebuilt on purpose");
        }
        storeVerified(pin, path, downloaded, digest);
        return new Asset(path, downloaded);
    }

    private byte[] downloaded(LibraryPin pin, String url) {
        try {
            return download.fetch(url);
        } catch (IOException failure) {
            throw refusal(pin, url + " could not be downloaded: " + failure.getMessage(),
                    "check the network and that release " + pin.tag() + " of " + pin.repository() + " publishes this asset, then start the host again");
        }
    }

    /**
     * Moves a verified download into place, accepting a destination another host already put there.
     *
     * @implNote Written beside its final name and moved into place, so a run killed mid-write leaves
     * a stray temporary file rather than a truncated asset under the real name. On Windows a move
     * onto a DLL another process has loaded fails with {@code AccessDeniedException} (measured);
     * when two hosts share one cache that DLL is the very file this host was about to write, so the
     * destination is re-hashed and accepted when it matches the pin.
     */
    private void storeVerified(LibraryPin pin, Path path, byte[] bytes, String digest) {
        Path temporary = path.resolveSibling(path.getFileName() + "." + UUID.randomUUID() + ".tmp");
        try {
            Files.createDirectories(path.getParent());
            Files.write(temporary, bytes);
            move.move(temporary, path);
        } catch (IOException failure) {
            byte[] present = readIfPresent(path);
            if (present == null || !sha256Of(present).equals(digest)) {
                throw refusal(pin, path + " could not be written: " + failure,
                        "free the cached file or point the library cache at a writable directory, then start the host again");
            }
        } finally {
            deleteQuietly(temporary);
        }
    }

    private static void deleteQuietly(Path temporary) {
        try {
            Files.deleteIfExists(temporary);
        } catch (IOException ignored) {
            // A leftover temporary file is harmless: it never carries a name the cache reads.
        }
    }

    private static byte[] readIfPresent(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException absentOrUnreadable) {
            return null;
        }
    }

    private static String sha256Of(byte[] bytes) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(digest.length * 2);
            for (byte octet : digest) {
                hex.append(String.format("%02x", octet & 0xff));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("every JVM provides SHA-256", impossible);
        }
    }

    private static LibraryDeclaration declarationOf(LibraryPin pin, byte[] bytes) {
        try {
            return LibraryDeclaration.parse(StrictJson.decodeUtf8(bytes));
        } catch (CharacterCodingException notUtf8) {
            throw refusal(pin, DECLARATION_ASSET + " is not UTF-8", "publish library.json as UTF-8");
        }
    }
}
