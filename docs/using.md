# Using it

The layer needs Java 8 or later, JNA on the class path below Java 22, and the core's shared binary. Java 22 and later
refuse, or warn about, native access a launch did not grant, so a process using the layer from the class path there
runs with `--enable-native-access=ALL-UNNAMED`. Any thread may call; a call into the core is a short downcall, during
which a virtual thread pins its carrier.

A host loads the core's binary with `CoreBinary.load` from a path it chooses ([the core module](modules/core.md)) and
hands it to `Host.create` ([the host module](modules/host/README.md)); reading manifest files and finding
plugins on disk are the optional [standard module](modules/standard.md)'s.
