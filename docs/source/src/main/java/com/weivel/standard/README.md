`com.weivel.standard`, optional: the standard's binary loaded through the same native seam, its answers copied into Java values, and its service definitions as Java interfaces. No class of `core`, `handles`, `timer`, `context` or `host` depends on it. What it reads and answers is [the standard module](../../../../../../../modules/standard.md).

<!-- generated: source-children -->
- [Config.java](../../../../../../../../src/main/java/com/weivel/standard/Config.java) - the standard's config service as a Java interface
- [DiscoveredPlugin.java](../../../../../../../../src/main/java/com/weivel/standard/DiscoveredPlugin.java) - one plugin the standard found on disk: its manifest, its files and its entry path
- [Discovery.java](../../../../../../../../src/main/java/com/weivel/standard/Discovery.java) - what one scan of a directory found, and every file it could not take a plugin from
- [Identity.java](../../../../../../../../src/main/java/com/weivel/standard/Identity.java) - a repository identity as the standard takes it apart
- [Paths.java](../../../../../../../../src/main/java/com/weivel/standard/Paths.java) - the standard's paths service as a Java interface
- [ReleaseObserver.java](../../../../../../../../src/main/java/com/weivel/standard/ReleaseObserver.java) - told of every struct handed to one of the standard's releases, for the tests
- [Secrets.java](../../../../../../../../src/main/java/com/weivel/standard/Secrets.java) - the standard's secrets service as a Java interface
- [ServiceRefusal.java](../../../../../../../../src/main/java/com/weivel/standard/ServiceRefusal.java) - why a provider of config, secrets or paths declined, thrown where C answers refused
- [SettingValue.java](../../../../../../../../src/main/java/com/weivel/standard/SettingValue.java) - one setting's value: text, a finite number, or a boolean
- [Standard.java](../../../../../../../../src/main/java/com/weivel/standard/Standard.java) - every default the standard ships, reached through one loaded binary and answered as Java values
- [StandardAnswer.java](../../../../../../../../src/main/java/com/weivel/standard/StandardAnswer.java) - what the standard answered: its status, the value, its refusal and every invalid field
- [StandardBinary.java](../../../../../../../../src/main/java/com/weivel/standard/StandardBinary.java) - the standard's shared binary, loaded from its caller's path, and every function this package calls
- [StandardFunction.java](../../../../../../../../src/main/java/com/weivel/standard/StandardFunction.java) - every function of the standard this package calls, with its symbol and signature
- [StandardLayouts.java](../../../../../../../../src/main/java/com/weivel/standard/StandardLayouts.java) - every public struct of the standard's headers, named as its header names it
- [StandardReads.java](../../../../../../../../src/main/java/com/weivel/standard/StandardReads.java) - copies the structs the standard fills into Java values
- [StandardRefusal.java](../../../../../../../../src/main/java/com/weivel/standard/StandardRefusal.java) - why the standard refused something: its code, its two texts and the field's path
- [StandardStatus.java](../../../../../../../../src/main/java/com/weivel/standard/StandardStatus.java) - how the standard answered a call
- [ValidationOptions.java](../../../../../../../../src/main/java/com/weivel/standard/ValidationOptions.java) - options for validating a manifest, as a value
- [package-info.java](../../../../../../../../src/main/java/com/weivel/standard/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
