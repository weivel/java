package com.weivel.core;

import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Finds a text of the caller's that C would read cut short: one holding a NUL character, where C stops reading.
 *
 * @implNote Such a text is the caller's mistake, refused before it is laid out for the core, because the core would
 * otherwise take the part before the NUL for the whole and act on a value the caller never gave.
 */
final class CallerTexts {
    private static final char NUL = '\u0000';

    private CallerTexts() {
    }

    static Optional<String> inText(String what, String text) {
        return text.indexOf(NUL) >= 0
                ? Optional.of(what + " holds a NUL character, which C would read as its end")
                : Optional.empty();
    }

    static Optional<String> inManifest(Manifest manifest) {
        return firstOf(Stream.of(
                inText("the manifest's import name", manifest.importName()),
                inOptional("the manifest's entry", manifest.entry()),
                inTexts("a target of the manifest", manifest.targets()),
                inTexts("a capability the manifest provides", manifest.capabilitiesProvided()),
                inTexts("a capability the manifest consumes", manifest.capabilitiesConsumed()),
                firstOf(manifest.servicesProvided().stream().map(CallerTexts::inOffer)),
                firstOf(manifest.servicesConsumed().stream().map(CallerTexts::inWant)),
                inTexts("a permission of the manifest", manifest.permissions()),
                inOptional("the manifest's identity", manifest.identity())));
    }

    static Optional<String> inOptions(CoreOptions options) {
        return firstOf(Stream.of(
                inTexts("an application of the core's options", options.applications()),
                inTexts("a well-known service of the core's options", options.wellKnownServices()),
                firstOf(options.constraintVocabulary().stream().map(entry -> firstOf(Stream.of(
                        inText("a constraint name of the vocabulary", entry.constraintName()),
                        inText("an attribute name of the vocabulary", entry.attributeName())))))));
    }

    static Optional<String> inReason(QuarantineReason reason) {
        return firstOf(Stream.of(inText("the reason's detail", reason.detail()),
                inText("the reason's fix", reason.fix())));
    }

    static Optional<String> inNumbers(String what, List<NamedNumber> numbers) {
        return firstOf(numbers.stream().map(number -> inText(what, number.name())));
    }

    private static Optional<String> inOffer(Manifest.ServiceOffer offer) {
        return firstOf(Stream.of(inText("a service id the manifest provides", offer.id()),
                inNumbers("an attribute name the manifest advertises", offer.attributes())));
    }

    private static Optional<String> inWant(Manifest.ServiceWant want) {
        return firstOf(Stream.of(inText("a service id the manifest consumes", want.id()),
                inOptional("a provider the manifest binds a service to", want.provider()),
                inNumbers("a constraint name of the manifest", want.constraints())));
    }

    private static Optional<String> inOptional(String what, Optional<String> text) {
        return text.flatMap(present -> inText(what, present));
    }

    static Optional<String> inTexts(String what, List<String> texts) {
        return firstOf(texts.stream().map(text -> inText(what, text)));
    }

    private static Optional<String> firstOf(Stream<Optional<String>> found) {
        return found.filter(Optional::isPresent).map(Optional::get).findFirst();
    }
}
