`com.weivel.benchmarks`: the timing gates, one benchmark class per gate, the sampling and summaries they share, and the bounds read from the pinned kit. What each gate times and how it is judged is [the benchmarks module](../../../../../../../modules/benchmarks.md); running them is [building.md](../../../../../../../building.md).

<!-- generated: source-children -->
- [ControlCallBenchmark.java](../../../../../../../../src/main/java/com/weivel/benchmarks/ControlCallBenchmark.java) - `control-call`: `weivel_get_service` through the wrapper over the same function called bare
- [CoreWorld.java](../../../../../../../../src/main/java/com/weivel/benchmarks/CoreWorld.java) - a core, its contexts and its timer composed as a host composes them, with a provider and a consumer
- [DefinitionCallBenchmark.java](../../../../../../../../src/main/java/com/weivel/benchmarks/DefinitionCallBenchmark.java) - the definition row of `library-call`: the echo definition's `echo` through a resolved definition over the direct pointer
- [DescribedCallBenchmark.java](../../../../../../../../src/main/java/com/weivel/benchmarks/DescribedCallBenchmark.java) - `described-call`: the described mirror's `reflect` through a bound object over the same operation called bare
- [DescribedCallMeasurement.java](../../../../../../../../src/main/java/com/weivel/benchmarks/DescribedCallMeasurement.java) - the described call's pair, with the bytes call through the host sampled in the same rounds
- [Gates.java](../../../../../../../../src/main/java/com/weivel/benchmarks/Gates.java) - the timing gates, judged on per-round medians against the bounds
- [Interleaved.java](../../../../../../../../src/main/java/com/weivel/benchmarks/Interleaved.java) - samples paths side by side, one batch of each a round, the first path moving each round
- [KitPlatform.java](../../../../../../../../src/main/java/com/weivel/benchmarks/KitPlatform.java) - the kit's name for the system a run is on
- [LanguageBudget.java](../../../../../../../../src/main/java/com/weivel/benchmarks/LanguageBudget.java) - the bounds the kit sets for Java, read from the pinned kit's `benchmarks/java.txt`
- [LibraryCallBenchmark.java](../../../../../../../../src/main/java/com/weivel/benchmarks/LibraryCallBenchmark.java) - `library-call`: the echo library's `echo_echo` through the host over the same function called bare
- [MeasuredPath.java](../../../../../../../../src/main/java/com/weivel/benchmarks/MeasuredPath.java) - one measured path, its answers kept so no call can be dropped as unread
- [NoiseBounds.java](../../../../../../../../src/main/java/com/weivel/benchmarks/NoiseBounds.java) - one platform's noise bounds
- [PairMeasurement.java](../../../../../../../../src/main/java/com/weivel/benchmarks/PairMeasurement.java) - a call through weivel timed against the same C function called bare
- [RatioGate.java](../../../../../../../../src/main/java/com/weivel/benchmarks/RatioGate.java) - a gate on how many times a bare C call's cost a call through weivel may take
- [SameLanguageBenchmark.java](../../../../../../../../src/main/java/com/weivel/benchmarks/SameLanguageBenchmark.java) - the same-language gate: a consumer is answered the provider's own object
- [SameLanguageMeasurement.java](../../../../../../../../src/main/java/com/weivel/benchmarks/SameLanguageMeasurement.java) - a call on a resolved service timed against the same call on the provider's object, and the direct call twice
- [Sampling.java](../../../../../../../../src/main/java/com/weivel/benchmarks/Sampling.java) - how a path is sampled: warm-up calls, then one timed batch per round
- [Summary.java](../../../../../../../../src/main/java/com/weivel/benchmarks/Summary.java) - samples summarised: the smallest, the quartiles, the median, the 99th percentile and the largest
- [Verdict.java](../../../../../../../../src/main/java/com/weivel/benchmarks/Verdict.java) - what a gate decided of one run, and the sentence saying why
- [package-info.java](../../../../../../../../src/main/java/com/weivel/benchmarks/package-info.java) - the package's summary in its Javadoc
<!-- end generated -->
