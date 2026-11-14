package com.weivel.standard;

import static com.weivel.standard.StandardReads.offset;

import com.weivel.core.ForeignMemory;
import com.weivel.core.Layout;
import com.weivel.core.Manifest;
import com.weivel.host.LibraryDeclaration;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Every default the standard ships, reached through one loaded {@link StandardBinary}: manifests, discovery, library
 * declarations, identities, the mirror list, SHA-256 and the ids of the config, secrets and paths services. Each
 * answer is copied into Java values, and every struct the standard filled is released through the standard's own
 * release before the call answers, on every path. One instance is safe for concurrent calls, each holding memory of
 * its own.
 */
public final class Standard {
    private static final int SHA256_BYTES = 32;
    private static final int SHA256_HEX_LENGTH = 64;
    private static final long POINTER = Layout.ADDRESS.byteSize();

    private final StandardBinary binary;
    private final String configServiceId;
    private final String secretsServiceId;
    private final String pathsServiceId;

    private Standard(StandardBinary binary) {
        this.binary = binary;
        this.configServiceId = serviceId(StandardFunction.SERVICE_CONFIG);
        this.secretsServiceId = serviceId(StandardFunction.SERVICE_SECRETS);
        this.pathsServiceId = serviceId(StandardFunction.SERVICE_PATHS);
    }

    /**
     * The defaults of a loaded standard, its service ids read from it at once.
     *
     * @param binary the standard's binary, which the caller closes once nothing more is asked of it
     * @return the defaults
     */
    public static Standard of(StandardBinary binary) {
        return new Standard(Objects.requireNonNull(binary, "binary"));
    }

    /**
     * The config service's id, as the standard answers it.
     *
     * @return the id
     */
    public String configServiceId() {
        return configServiceId;
    }

    /**
     * The secrets service's id, as the standard answers it.
     *
     * @return the id
     */
    public String secretsServiceId() {
        return secretsServiceId;
    }

    /**
     * The paths service's id, as the standard answers it.
     *
     * @return the id
     */
    public String pathsServiceId() {
        return pathsServiceId;
    }

    /**
     * Validates a JSON manifest and, only when it is valid, reads it into a manifest value, as {@code
     * weivel_standard_load_manifest} does. The value carries no identity, which whatever loaded the plugin supplies.
     *
     * @param json the document
     * @param options the validation's options
     * @return the manifest on OK; the first wrong field as the refusal, and every one in {@code paths}
     */
    public StandardAnswer<Manifest> loadManifest(String json, ValidationOptions options) {
        byte[] document = utf8(json);
        try (ForeignMemory memory = binary.memory()) {
            long text = memory.bytes(document);
            long optionsStruct = validationOptions(memory, Objects.requireNonNull(options, "options"));
            long loaded = memory.allocate(POINTER);
            long validation = sized(memory, StandardLayouts.VALIDATION);
            try {
                StandardStatus status = status(StandardFunction.LOAD_MANIFEST, text, document.length, optionsStruct,
                        loaded, validation);
                Optional<Manifest> manifest = status == StandardStatus.OK
                        ? Optional.of(memory.manifest(view(memory.getLong(loaded, 0))))
                        : Optional.<Manifest>empty();
                return new StandardAnswer<>(status, manifest,
                        StandardReads.refusal(memory, validation + offset(StandardLayouts.VALIDATION, "refusal")),
                        StandardReads.strings(memory, validation + offset(StandardLayouts.VALIDATION, "paths")));
            } finally {
                releaseBoth(StandardFunction.MANIFEST_RELEASE, memory.getLong(loaded, 0),
                        StandardFunction.VALIDATION_RELEASE, validation);
            }
        }
    }

    /**
     * Reads a JSON manifest into a manifest value WITHOUT validating it, as {@code weivel_standard_read_manifest}
     * does: a field left out takes the core's default, and a field the core's manifest has no place for is ignored. A
     * host taking the standard's defaults calls {@link #loadManifest}, which validates first.
     *
     * @param json the document
     * @return the manifest on OK; REFUSED for malformed JSON, a wrong type or an unrepresentable value
     * @throws IllegalArgumentException for a document the standard read with no {@code importName}, which the
     *     core's manifest value cannot hold and the core refuses on registration
     */
    public StandardAnswer<Manifest> readManifest(String json) {
        byte[] document = utf8(json);
        try (ForeignMemory memory = binary.memory()) {
            long text = memory.bytes(document);
            long read = memory.allocate(POINTER);
            long refusal = sized(memory, StandardLayouts.REFUSAL);
            try {
                StandardStatus status = status(StandardFunction.READ_MANIFEST, text, document.length, read, refusal);
                Optional<Manifest> manifest = status == StandardStatus.OK
                        ? Optional.of(memory.manifest(view(memory.getLong(read, 0))))
                        : Optional.<Manifest>empty();
                return StandardAnswer.of(status, manifest, StandardReads.refusal(memory, refusal));
            } finally {
                releaseBoth(StandardFunction.MANIFEST_RELEASE, memory.getLong(read, 0),
                        StandardFunction.REFUSAL_RELEASE, refusal);
            }
        }
    }

    /**
     * Validates a JSON manifest by the standard's schema and rules V1 and V2, without reading it, as {@code
     * weivel_standard_validate} does.
     *
     * @param json the document
     * @param options the validation's options
     * @return OK for a valid manifest; REFUSED with the first wrong field as the refusal, and every one in
     *     {@code paths}
     */
    public StandardAnswer<Void> validate(String json, ValidationOptions options) {
        byte[] document = utf8(json);
        try (ForeignMemory memory = binary.memory()) {
            long text = memory.bytes(document);
            long optionsStruct = validationOptions(memory, Objects.requireNonNull(options, "options"));
            long validation = sized(memory, StandardLayouts.VALIDATION);
            try {
                StandardStatus status = status(StandardFunction.VALIDATE, text, document.length, optionsStruct,
                        validation);
                return new StandardAnswer<>(status, Optional.<Void>empty(),
                        StandardReads.refusal(memory, validation + offset(StandardLayouts.VALIDATION, "refusal")),
                        StandardReads.strings(memory, validation + offset(StandardLayouts.VALIDATION, "paths")));
            } finally {
                binary.release(StandardFunction.VALIDATION_RELEASE, validation);
            }
        }
    }

    /**
     * Scans one directory, not its subdirectories, for plugins laid out by the standard, as {@code
     * weivel_standard_discover} does.
     *
     * @param directory the directory
     * @param options passed to the validation of every manifest
     * @return what was found on OK, whatever the files held; REFUSED with NOT_FOUND or UNREADABLE for the directory
     * @throws IllegalArgumentException for a path holding a NUL character
     */
    public StandardAnswer<Discovery> discover(Path directory, ValidationOptions options) {
        Objects.requireNonNull(directory, "directory");
        try (ForeignMemory memory = binary.memory()) {
            long path = memory.text(directory.toString());
            long optionsStruct = validationOptions(memory, Objects.requireNonNull(options, "options"));
            long discovery = sized(memory, StandardLayouts.DISCOVERY);
            try {
                StandardStatus status = status(StandardFunction.DISCOVER, path, optionsStruct, discovery);
                Optional<Discovery> found = status == StandardStatus.OK
                        ? Optional.of(StandardReads.discovery(memory, discovery, this::view))
                        : Optional.<Discovery>empty();
                return StandardAnswer.of(status, found,
                        StandardReads.refusal(memory, discovery + offset(StandardLayouts.DISCOVERY, "refusal")));
            } finally {
                binary.release(StandardFunction.DISCOVERY_RELEASE, discovery);
            }
        }
    }

    /**
     * Reads a C library's declaration, which {@code Host.loadLibrary} loads beside the library's path.
     *
     * @param json the document
     * @return the declaration on OK; the first field the rules refuse as the refusal
     */
    public StandardAnswer<LibraryDeclaration> readLibrary(String json) {
        byte[] document = utf8(json);
        try (ForeignMemory memory = binary.memory()) {
            long text = memory.bytes(document);
            long library = sized(memory, StandardLayouts.LIBRARY);
            long refusal = sized(memory, StandardLayouts.REFUSAL);
            try {
                StandardStatus status = status(StandardFunction.READ_LIBRARY, text, document.length, library,
                        refusal);
                Optional<LibraryDeclaration> declaration = status == StandardStatus.OK
                        ? Optional.of(StandardReads.library(memory, library))
                        : Optional.<LibraryDeclaration>empty();
                return StandardAnswer.of(status, declaration, StandardReads.refusal(memory, refusal));
            } finally {
                releaseBoth(StandardFunction.LIBRARY_RELEASE, library, StandardFunction.REFUSAL_RELEASE, refusal);
            }
        }
    }

    /**
     * Takes a repository identity apart, as {@code weivel_standard_identity_parse} does.
     *
     * @param text the identity
     * @return its parts on OK; INVALID_VALUE or UNREPRESENTABLE as the refusal
     */
    public StandardAnswer<Identity> parseIdentity(String text) {
        try (ForeignMemory memory = binary.memory()) {
            ParsedIdentity parsed = parse(memory, text);
            try {
                Optional<Identity> identity = parsed.status == StandardStatus.OK
                        ? Optional.of(StandardReads.identity(memory, parsed.identity))
                        : Optional.<Identity>empty();
                return StandardAnswer.of(parsed.status, identity, parsed.refusal);
            } finally {
                parsed.release();
            }
        }
    }

    /**
     * Orders two identities as {@code weivel_standard_identity_compare} does.
     *
     * @param left the first identity
     * @param right the second
     * @return below zero, zero or above zero on OK; the refusal of the first that is not an identity
     */
    public StandardAnswer<Integer> compareIdentities(String left, String right) {
        try (ForeignMemory memory = binary.memory()) {
            ParsedIdentity first = parse(memory, left);
            try {
                if (first.status != StandardStatus.OK) {
                    return StandardAnswer.of(first.status, Optional.<Integer>empty(), first.refusal);
                }
                ParsedIdentity second = parse(memory, right);
                try {
                    if (second.status != StandardStatus.OK) {
                        return StandardAnswer.of(second.status, Optional.<Integer>empty(), second.refusal);
                    }
                    int order = (int) binary.call(StandardFunction.IDENTITY_COMPARE, first.identity,
                            second.identity);
                    return StandardAnswer.of(StandardStatus.OK, Optional.of(order),
                            Optional.<StandardRefusal>empty());
                } finally {
                    second.release();
                }
            } finally {
                first.release();
            }
        }
    }

    /**
     * Reads a mirror list, {@code { "mirrors": [ "<template>", ... ] }}, as {@code weivel_standard_read_mirrors}
     * does.
     *
     * @param json the document
     * @return the templates in file order on OK; the template refused as the refusal's path
     */
    public StandardAnswer<List<String>> readMirrors(String json) {
        byte[] document = utf8(json);
        try (ForeignMemory memory = binary.memory()) {
            long text = memory.bytes(document);
            return mirrors(memory, StandardFunction.READ_MIRRORS, text, document.length);
        }
    }

    /**
     * Reads the mirror list in a file, as {@code weivel_standard_load_mirrors} does.
     *
     * @param file the file, a path the caller chose
     * @return the templates on OK; NOT_FOUND or UNREADABLE for the file itself
     * @throws IllegalArgumentException for a path holding a NUL character
     */
    public StandardAnswer<List<String>> loadMirrors(Path file) {
        try (ForeignMemory memory = binary.memory()) {
            long path = memory.text(Objects.requireNonNull(file, "file").toString());
            return mirrors(memory, StandardFunction.LOAD_MIRRORS, path);
        }
    }

    /**
     * Expands a mirror template for a download, as {@code weivel_standard_mirror_expand} does.
     *
     * @param template the template
     * @param hashHex the SHA-256 to fetch, 64 lowercase hex characters
     * @return the URL on OK; INVALID_VALUE for a hash or a template the standard refuses
     * @throws IllegalArgumentException for a text holding a NUL character
     */
    public StandardAnswer<String> expandMirror(String template, String hashHex) {
        try (ForeignMemory memory = binary.memory()) {
            long templateText = memory.text(Objects.requireNonNull(template, "template"));
            long hashText = memory.text(Objects.requireNonNull(hashHex, "hashHex"));
            long expanded = memory.allocate(POINTER);
            long refusal = sized(memory, StandardLayouts.REFUSAL);
            try {
                StandardStatus status = status(StandardFunction.MIRROR_EXPAND, templateText, hashText, expanded,
                        refusal);
                Optional<String> url = status == StandardStatus.OK
                        ? Optional.of(memory.readText(memory.getLong(expanded, 0)))
                        : Optional.<String>empty();
                return StandardAnswer.of(status, url, StandardReads.refusal(memory, refusal));
            } finally {
                releaseBoth(StandardFunction.TEXT_RELEASE, memory.getLong(expanded, 0),
                        StandardFunction.REFUSAL_RELEASE, refusal);
            }
        }
    }

    /**
     * The SHA-256 of some bytes, as the standard computes it.
     *
     * @param bytes the bytes
     * @return the 32-byte digest
     */
    public byte[] sha256(byte[] bytes) {
        try (ForeignMemory memory = binary.memory()) {
            long digest = memory.allocate(SHA256_BYTES);
            binary.call(StandardFunction.SHA256, memory.bytes(bytes), bytes.length, digest);
            return memory.readBytes(digest, 0, SHA256_BYTES);
        }
    }

    /**
     * The SHA-256 of some bytes as 64 lowercase hex characters, as the standard writes it.
     *
     * @param bytes the bytes
     * @return the digest in hex
     */
    public String sha256Hex(byte[] bytes) {
        try (ForeignMemory memory = binary.memory()) {
            long digest = memory.allocate(SHA256_HEX_LENGTH + 1L);
            binary.call(StandardFunction.SHA256_HEX, memory.bytes(bytes), bytes.length, digest);
            return memory.readText(digest);
        }
    }

    /**
     * Whether a text is exactly 64 lowercase hex characters, as the standard judges it.
     *
     * @param text the text
     * @return whether it is; false for a text holding a NUL character, which C would read cut short
     */
    public boolean isSha256Hex(String text) {
        if (text.indexOf('\0') >= 0) {
            return false;
        }
        try (ForeignMemory memory = binary.memory()) {
            return binary.call(StandardFunction.IS_SHA256_HEX, memory.text(text)) == 1;
        }
    }

    /**
     * Checks a download against the SHA-256 that was asked for; the standard never fetches.
     *
     * @param bytes the download
     * @param expectedHex the expected SHA-256, 64 lowercase hex characters
     * @return OK on a match; REFUSED with INVALID_VALUE on a mismatch, its detail naming both hashes
     * @throws IllegalArgumentException for an expected hash holding a NUL character
     */
    public StandardAnswer<Void> verifyDownload(byte[] bytes, String expectedHex) {
        try (ForeignMemory memory = binary.memory()) {
            long download = memory.bytes(bytes);
            long expected = memory.text(Objects.requireNonNull(expectedHex, "expectedHex"));
            long refusal = sized(memory, StandardLayouts.REFUSAL);
            try {
                StandardStatus status = status(StandardFunction.VERIFY_DOWNLOAD, download, bytes.length, expected,
                        refusal);
                return StandardAnswer.of(status, Optional.<Void>empty(), StandardReads.refusal(memory, refusal));
            } finally {
                binary.release(StandardFunction.REFUSAL_RELEASE, refusal);
            }
        }
    }

    private StandardAnswer<List<String>> mirrors(ForeignMemory memory, StandardFunction reader, long... source) {
        long list = sized(memory, StandardLayouts.MIRROR_LIST);
        long refusal = sized(memory, StandardLayouts.REFUSAL);
        try {
            long[] arguments = new long[source.length + 2];
            System.arraycopy(source, 0, arguments, 0, source.length);
            arguments[source.length] = list;
            arguments[source.length + 1] = refusal;
            StandardStatus status = status(reader, arguments);
            Optional<List<String>> templates = status == StandardStatus.OK
                    ? Optional.of(Collections.unmodifiableList(StandardReads.templates(memory, list)))
                    : Optional.<List<String>>empty();
            return StandardAnswer.of(status, templates, StandardReads.refusal(memory, refusal));
        } finally {
            releaseBoth(StandardFunction.MIRROR_LIST_RELEASE, list, StandardFunction.REFUSAL_RELEASE, refusal);
        }
    }

    private ParsedIdentity parse(ForeignMemory memory, String text) {
        byte[] bytes = utf8(text);
        long identity = sized(memory, StandardLayouts.IDENTITY);
        long refusal = sized(memory, StandardLayouts.REFUSAL);
        ParsedIdentity parsed = new ParsedIdentity(identity, refusal);
        try {
            parsed.status = status(StandardFunction.IDENTITY_PARSE, memory.bytes(bytes), bytes.length, identity,
                    refusal);
            parsed.refusal = StandardReads.refusal(memory, refusal);
            return parsed;
        } catch (RuntimeException | Error failed) {
            parsed.release();
            throw failed;
        }
    }

    /** Runs the second release whatever the first does, so a failing release leaks nothing else of its call. */
    private void releaseBoth(StandardFunction first, long firstStruct, StandardFunction second, long secondStruct) {
        try {
            binary.release(first, firstStruct);
        } finally {
            binary.release(second, secondStruct);
        }
    }

    private long view(long manifest) {
        return binary.call(StandardFunction.MANIFEST_VIEW, manifest);
    }

    private String serviceId(StandardFunction function) {
        try (ForeignMemory memory = binary.memory()) {
            return memory.readText(binary.call(function));
        }
    }

    private StandardStatus status(StandardFunction function, long... arguments) {
        return StandardStatus.of(binary.call(function, arguments));
    }

    private static long sized(ForeignMemory memory, Layout layout) {
        long struct = memory.allocate(layout.byteSize());
        memory.putInt(struct, offset(layout, "size"), (int) layout.byteSize());
        return struct;
    }

    private static long validationOptions(ForeignMemory memory, ValidationOptions options) {
        if (!options.wellKnownServices().isPresent()) {
            return 0;
        }
        List<String> ids = options.wellKnownServices().get();
        long items = memory.allocate(Math.max(1, ids.size()) * POINTER);
        for (int index = 0; index < ids.size(); index++) {
            memory.putLong(items, index * POINTER, memory.text(ids.get(index)));
        }
        Layout strings = StandardLayouts.STRINGS_OF_THE_CORE;
        long list = memory.allocate(strings.byteSize());
        memory.putLong(list, offset(strings, "items"), ids.isEmpty() ? 0 : items);
        memory.putInt(list, offset(strings, "count"), ids.size());
        long struct = sized(memory, StandardLayouts.VALIDATION_OPTIONS);
        memory.putLong(struct, offset(StandardLayouts.VALIDATION_OPTIONS, "well_known_services"), list);
        return struct;
    }

    private static byte[] utf8(String text) {
        return Objects.requireNonNull(text, "text").getBytes(StandardCharsets.UTF_8);
    }

    /** An identity the standard parsed into memory of the call's, released through the standard by its owner. */
    private final class ParsedIdentity {
        private final long identity;
        private final long refusalStruct;
        private StandardStatus status;
        private Optional<StandardRefusal> refusal = Optional.empty();

        private ParsedIdentity(long identity, long refusalStruct) {
            this.identity = identity;
            this.refusalStruct = refusalStruct;
        }

        private void release() {
            releaseBoth(StandardFunction.IDENTITY_RELEASE, identity, StandardFunction.REFUSAL_RELEASE, refusalStruct);
        }
    }
}
