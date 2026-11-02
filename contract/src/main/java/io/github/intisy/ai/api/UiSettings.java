package io.github.intisy.ai.api;

import io.github.intisy.ai.tsemit.TsIndexSignature;
import io.github.intisy.ai.tsemit.TsInterface;

/**
 * Settings keyed by setting key.
 *
 * @implNote An interface rather than a {@code Map}, because the model's {@code uiSettings}
 * definition resolves to a contract type by name, and a map has none.
 */
@TsInterface(data = true)
@TsIndexSignature(key = "key", value = "SettingDeclaration")
public interface UiSettings {
}
